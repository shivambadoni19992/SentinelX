package com.sentinelx.detection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.detection.engine.DetectionEngine;
import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.model.WindowStore;
import com.sentinelx.detection.rule.DetectionRule;
import com.sentinelx.detection.rule.auth.FailedLoginSpikeRule;
import com.sentinelx.detection.rule.auth.NewDeviceRule;
import com.sentinelx.detection.rule.auth.NewIpRule;
import com.sentinelx.detection.rule.api.ApiRequestSpikeRule;
import com.sentinelx.detection.rule.api.BotActivityRule;
import com.sentinelx.detection.rule.audit.PrivilegedAccessAnomalyRule;
import com.sentinelx.detection.rule.audit.UnauthorizedDataAccessRule;
import com.sentinelx.detection.rule.network.ConnectionSpikeRule;
import com.sentinelx.detection.rule.network.PortScanRule;
import com.sentinelx.detection.rule.network.SuspiciousOutboundRule;
import com.sentinelx.detection.rule.payment.MultipleFailedPaymentsRule;
import com.sentinelx.detection.rule.payment.TransactionVelocityRule;
import com.sentinelx.detection.rule.payment.UnusualTransactionAmountRule;

/**
 * Verifies the composable wiring of the engine: all 13 rules are discovered,
 * topic applicability is respected, multiple rules can fire on one event with
 * their contributions aggregated, and a failing rule is isolated.
 */
class DetectionEngineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DetectionContext ctx(String topic, String json) {
        try {
            return new DetectionContext(topic, "k", "corr", MAPPER.readTree(json),
                    Instant.parse("2026-09-03T12:00:00Z"), new WindowStore());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<DetectionRule> allRules() {
        return List.of(
                new FailedLoginSpikeRule(), new NewDeviceRule(), new NewIpRule(),
                new UnusualTransactionAmountRule(), new TransactionVelocityRule(),
                new MultipleFailedPaymentsRule(),
                new ApiRequestSpikeRule(), new BotActivityRule(),
                new PortScanRule(), new ConnectionSpikeRule(), new SuspiciousOutboundRule(),
                new UnauthorizedDataAccessRule(), new PrivilegedAccessAnomalyRule());
    }

    @Test
    void composesAllThirteenRules() {
        DetectionEngine engine = new DetectionEngine(allRules());
        assertThat(engine.rules()).hasSize(13);
        assertThat(engine.rules().stream().map(DetectionRule::id)).containsExactlyInAnyOrder(
                "FAILED_LOGIN_SPIKE", "NEW_DEVICE", "NEW_IP",
                "UNUSUAL_TRANSACTION_AMOUNT", "TRANSACTION_VELOCITY", "MULTIPLE_FAILED_PAYMENTS",
                "API_REQUEST_SPIKE", "BOT_ACTIVITY",
                "PORT_SCAN", "CONNECTION_SPIKE", "SUSPICIOUS_OUTBOUND",
                "UNAUTHORIZED_DATA_ACCESS", "PRIVILEGED_ACCESS_ANOMALY");
    }

    @Test
    void topicApplicabilityFiltersRules() {
        DetectionEngine engine = new DetectionEngine(allRules());
        // a benign payment event must not trigger any rule
        DetectionEngine.Evaluation e = engine.evaluate(ctx("security.payment",
                "{\"eventType\":\"PAYMENT_CREATED\",\"customerId\":\"c\",\"amount\":100}"));
        assertThat(e.matches()).isEmpty();
        assertThat(e.aggregateRisk()).isZero();
    }

    @Test
    void noMatchesYieldZeroRisk() {
        DetectionEngine engine = new DetectionEngine(allRules());
        DetectionEngine.Evaluation e = engine.evaluate(ctx("security.auth",
                "{\"eventType\":\"LOGIN_SUCCESS\",\"username\":\"u\"}"));
        assertThat(e.matches()).isEmpty();
        assertThat(e.aggregateRisk()).isZero();
    }

    @Test
    void aggregatesMultipleMatchesAndCapsRisk() {
        DetectionRule alwaysHigh = new DetectionRule() {
            @Override
            public String id() {
                return "ALWAYS";
            }

            @Override
            public DetectionResult evaluate(DetectionContext context) {
                return new DetectionResult(id(), Severity.HIGH, 60, "test", "test");
            }
        };
        DetectionRule alwaysCritical = new DetectionRule() {
            @Override
            public String id() {
                return "ALSO_ALWAYS";
            }

            @Override
            public DetectionResult evaluate(DetectionContext context) {
                return new DetectionResult(id(), Severity.CRITICAL, 45, "test", "test");
            }
        };
        DetectionEngine engine = new DetectionEngine(List.of(alwaysHigh, alwaysCritical));
        DetectionEngine.Evaluation e = engine.evaluate(ctx("security.auth",
                "{\"eventType\":\"LOGIN_FAILED\"}"));
        assertThat(e.matches()).extracting(DetectionResult::ruleId)
                .containsExactlyInAnyOrder("ALWAYS", "ALSO_ALWAYS");
        // 60 + 45 = 105, capped at 100
        assertThat(e.aggregateRisk()).isEqualTo(100);
    }

    @Test
    void isolatesFailingRules() {
        DetectionRule broken = new DetectionRule() {
            @Override
            public String id() {
                return "BROKEN";
            }

            @Override
            public DetectionResult evaluate(DetectionContext context) {
                throw new IllegalStateException("boom");
            }
        };
        DetectionRule healthy = new DetectionRule() {
            @Override
            public String id() {
                return "HEALTHY";
            }

            @Override
            public DetectionResult evaluate(DetectionContext context) {
                return new DetectionResult(id(), Severity.LOW, 5, "ok", "ok");
            }
        };
        DetectionEngine engine = new DetectionEngine(List.of(broken, healthy));
        DetectionEngine.Evaluation e = engine.evaluate(ctx("security.api", "{\"eventType\":\"API_REQUEST\"}"));
        assertThat(e.matches()).extracting(DetectionResult::ruleId).containsExactly("HEALTHY");
        assertThat(e.aggregateRisk()).isEqualTo(5);
    }
}
