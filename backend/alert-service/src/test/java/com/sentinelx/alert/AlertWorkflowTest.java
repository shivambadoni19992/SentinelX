package com.sentinelx.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClient;

import com.sentinelx.alert.domain.AlertAction;
import com.sentinelx.alert.domain.AlertStatus;
import com.sentinelx.alert.entity.SecurityAlert;
import com.sentinelx.alert.repository.SecurityAlertRepository;
import com.sentinelx.alert.service.AlertWorkflowService;
import com.sentinelx.alert.service.AuditEventPublisher;
import com.sentinelx.alert.service.ResponseActionExecutor;

/**
 * Unit coverage for the alert response workflow: actions drive real state
 * (downstream HTTP, Redis) and every mutation emits an audit event.
 */
class AlertWorkflowTest {

    private SecurityAlertRepository repository;
    private AuditEventPublisher audit;
    private AlertWorkflowService workflow;

    private StringRedisTemplate redis;
    private ValueOperations<String, String> redisValues;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(SecurityAlertRepository.class);
        audit = mock(AuditEventPublisher.class);
        redis = mock(StringRedisTemplate.class);
        redisValues = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(redisValues);
        when(repository.save(any(SecurityAlert.class)))
                .thenAnswer(inv -> inv.getArgument(0, SecurityAlert.class));
        workflow = new AlertWorkflowService(repository,
                executorFor(RestClient.builder(), RestClient.builder()), audit);
    }

    private ResponseActionExecutor executorFor(RestClient.Builder paymentBuilder, RestClient.Builder authBuilder) {
        return new ResponseActionExecutor(
                paymentBuilder, authBuilder,
                "http://payment.test", "http://auth.test",
                Duration.ofMinutes(15), Duration.ofHours(24), redis);
    }

    private SecurityAlert alert(String entityType, UUID entityId) {
        SecurityAlert a = new SecurityAlert();
        a.setTitle("Test alert");
        a.setSeverity("HIGH");
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setStatus("OPEN");
        return a;
    }

    /** Stubs the repository to return this alert and hands back a valid id. */
    private UUID stubbed(String entityType, UUID entityId) {
        SecurityAlert a = alert(entityType, entityId);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(a));
        return id;
    }

    @Test
    @DisplayName("RATE_LIMIT applies a Redis block key with TTL and audits the action")
    void rateLimitAppliesRedisState() {
        SecurityAlert a = workflow.applyAction(stubbed("RISK_SUBJECT", UUID.randomUUID()),
                AlertAction.RATE_LIMIT, "analyst-1");

        assertThat(a.getAction()).isEqualTo("RATE_LIMIT");
        assertThat(a.getActor()).isEqualTo("analyst-1");
        assertThat(a.getActionDetail())
                .containsEntry("state", "APPLIED")
                .hasEntrySatisfying("redisKey",
                        k -> assertThat((String) k).startsWith("sentinelx:ratelimit:block:"));
        verify(redisValues).set(anyString(), anyString(), any(Duration.class));
        verify(audit).alertActionApplied(any(), Mockito.eq("RATE_LIMIT"), Mockito.eq("analyst-1"), any());
    }

    @Test
    @DisplayName("REQUIRE_VERIFICATION and MONITOR set their Redis flags")
    void verificationAndMonitorSetRedis() {
        workflow.applyAction(stubbed("RISK_SUBJECT", null), AlertAction.REQUIRE_VERIFICATION, "a");
        verify(redisValues).set(Mockito.startsWith("sentinelx:verification:required:"), anyString(), any());

        workflow.applyAction(stubbed("RISK_SUBJECT", null), AlertAction.MONITOR, "a");
        verify(redisValues).set(Mockito.startsWith("sentinelx:monitor:"), anyString(), any());
    }

    @Test
    @DisplayName("HOLD_TRANSACTION skips gracefully when no payment is bound")
    void holdTransactionSkipsWithoutPayment() {
        SecurityAlert a = workflow.applyAction(stubbed("RISK_SUBJECT", null),
                AlertAction.HOLD_TRANSACTION, "analyst-2");
        assertThat(a.getActionDetail()).containsEntry("state", "SKIPPED — no payment bound to this alert");
        verify(audit).alertActionApplied(any(), Mockito.eq("HOLD_TRANSACTION"), any(), any());
    }

    @Test
    @DisplayName("HOLD_TRANSACTION against payment-service records HELD state (mocked server)")
    void holdTransactionCallsPaymentService() {
        UUID paymentId = UUID.randomUUID();
        RestClient.Builder sharedBuilder = RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(sharedBuilder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .requestTo("http://payment.test/internal/payments/" + paymentId + "/status"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withSuccess());
        ResponseActionExecutor executor = executorFor(sharedBuilder, RestClient.builder());

        Map<String, Object> detail = executor.apply(alert("PAYMENT", paymentId),
                AlertAction.HOLD_TRANSACTION);
        assertThat(detail).containsEntry("state", "APPLIED").containsEntry("paymentStatus", "HELD");
        server.verify();
    }

    @Test
    @DisplayName("BLOCK_ACCOUNT against auth-service records BLOCKED state (mocked server)")
    void blockAccountCallsAuthService() {
        UUID userId = UUID.randomUUID();
        RestClient.Builder sharedBuilder = RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(sharedBuilder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .requestTo("http://auth.test/internal/users/" + userId + "/status"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withSuccess());
        ResponseActionExecutor executor = executorFor(RestClient.builder(), sharedBuilder);

        Map<String, Object> detail = executor.apply(alert("USER", userId), AlertAction.BLOCK_ACCOUNT);
        assertThat(detail).containsEntry("state", "APPLIED").containsEntry("accountStatus", "BLOCKED");
        server.verify();
    }

    @Test
    @DisplayName("ALLOW records an explicit allow without downstream calls")
    void allowRecordsOnly() {
        SecurityAlert a = workflow.applyAction(stubbed("RISK_SUBJECT", null), AlertAction.ALLOW, "a");
        assertThat(a.getActionDetail()).containsEntry("state", "ALLOWED — no downstream change");
        verify(redisValues, never()).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("status lifecycle changes persist and emit audit events")
    void statusLifecycleAudited() {
        SecurityAlert stored = alert("RISK_SUBJECT", null);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(stored));

        SecurityAlert a = workflow.changeStatus(id, AlertStatus.INVESTIGATING, "analyst-3");
        assertThat(a.getStatus()).isEqualTo("INVESTIGATING");
        assertThat(a.getActor()).isEqualTo("analyst-3");
        verify(audit).alertStatusChanged(id, "OPEN", "INVESTIGATING", "analyst-3");
    }

    @Test
    @DisplayName("invalid statuses, actions and unknown alerts are rejected")
    void invalidInputRejected() {
        assertThatThrownBy(() -> AlertStatus.of("NOT_A_STATUS")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AlertAction.of("NOT_AN_ACTION")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AlertAction.of(" ")).isInstanceOf(IllegalArgumentException.class);
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> workflow.changeStatus(UUID.randomUUID(), AlertStatus.RESOLVED, "a"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workflow.applyAction(UUID.randomUUID(), AlertAction.RATE_LIMIT, "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}