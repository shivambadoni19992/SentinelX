package com.sentinelx.detection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.model.WindowStore;
import com.sentinelx.detection.rule.api.ApiRequestSpikeRule;
import com.sentinelx.detection.rule.api.BotActivityRule;
import com.sentinelx.detection.rule.auth.FailedLoginSpikeRule;
import com.sentinelx.detection.rule.auth.NewDeviceRule;
import com.sentinelx.detection.rule.auth.NewIpRule;
import com.sentinelx.detection.rule.audit.PrivilegedAccessAnomalyRule;
import com.sentinelx.detection.rule.audit.UnauthorizedDataAccessRule;
import com.sentinelx.detection.rule.network.ConnectionSpikeRule;
import com.sentinelx.detection.rule.network.PortScanRule;
import com.sentinelx.detection.rule.network.SuspiciousOutboundRule;
import com.sentinelx.detection.rule.payment.MultipleFailedPaymentsRule;
import com.sentinelx.detection.rule.payment.TransactionVelocityRule;
import com.sentinelx.detection.rule.payment.UnusualTransactionAmountRule;

/**
 * Exhaustive unit coverage for all 13 detection rules: match, threshold
 * boundary and no-match cases, plus verification that every result carries
 * ruleId, severity, riskContribution, reason and recommendedAction.
 */
class DetectionRulesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WindowStore windows;

    @BeforeEach
    void setUp() {
        windows = new WindowStore();
        windows.clear();
    }

    // ---------- helpers ----------

    private DetectionContext ctx(String topic, String key, String json, Instant at) {
        try {
            JsonNode payload = MAPPER.readTree(json);
            Map<String, Object> data = MAPPER.convertValue(payload, Map.class);
            data.put("topic", topic);
            DetectionContext context = new DetectionContext(topic, key, "corr-" + key, payload, at, windows);
            String ip = context.sourceIp();
            windows.record(context.subjectScope(), at, data);
            if (ip != null) {
                windows.record("ip:" + ip, at, data);
            }
            return context;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Records a prior event directly into both scopes (as the consumer does). */
    private void prior(String topic, String key, Map<String, Object> data, Instant at) {
        Map<String, Object> d = new HashMap<>(data);
        d.put("topic", topic);
        windows.record("subject:" + key, at, d);
        if (data.get("sourceIp") != null) {
            windows.record("ip:" + data.get("sourceIp"), at, d);
        }
    }

    private static void assertComplete(DetectionResult r, String ruleId) {
        assertThat(r.ruleId()).isEqualTo(ruleId);
        assertThat(r.severity()).isIn((Object[]) Severity.values());
        assertThat(r.riskContribution()).isBetween(1, 100);
        assertThat(r.reason()).isNotBlank();
        assertThat(r.recommendedAction()).isNotBlank();
    }

    // ---------- FAILED_LOGIN_SPIKE ----------

    @Test
    @DisplayName("FAILED_LOGIN_SPIKE fires at 5 failed logins in 5 minutes")
    void failedLoginSpikeFires() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        for (int i = 0; i < 4; i++) {
            prior("security.auth", "spike-user", Map.of("eventType", "LOGIN_FAILED", "username", "spike-user"),
                    now.minusSeconds(30));
        }
        DetectionResult r = new FailedLoginSpikeRule().evaluate(
                ctx("security.auth", "spike-user",
                        "{\"eventType\":\"LOGIN_FAILED\",\"username\":\"spike-user\"}", now));
        assertThat(r).isNotNull();
        assertComplete(r, FailedLoginSpikeRule.RULE_ID);
        assertThat(r.severity()).isEqualTo(Severity.HIGH);
        assertThat(r.reason()).contains("5 failed logins");
    }

    @Test
    @DisplayName("FAILED_LOGIN_SPIKE stays silent below threshold and for other event types")
    void failedLoginSpikeNegative() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        for (int i = 0; i < 3; i++) {
            prior("security.auth", "quiet-user", Map.of("eventType", "LOGIN_FAILED", "username", "quiet-user"),
                    now.minusSeconds(30));
        }
        assertThat(new FailedLoginSpikeRule().evaluate(ctx("security.auth", "quiet-user",
                "{\"eventType\":\"LOGIN_FAILED\",\"username\":\"quiet-user\"}", now))).isNull();
        assertThat(new FailedLoginSpikeRule().evaluate(ctx("security.auth", "quiet-user",
                "{\"eventType\":\"LOGIN_SUCCESS\",\"username\":\"quiet-user\"}", now))).isNull();
        // old failures outside the window do not count
        prior("security.auth", "aged-user", Map.of("eventType", "LOGIN_FAILED", "username", "aged-user"),
                now.minus(Duration.ofMinutes(10)));
        assertThat(new FailedLoginSpikeRule().evaluate(ctx("security.auth", "aged-user",
                "{\"eventType\":\"LOGIN_FAILED\",\"username\":\"aged-user\"}", now))).isNull();
    }

    // ---------- NEW_DEVICE ----------

    @Test
    @DisplayName("NEW_DEVICE fires once per unseen device, then stays silent")
    void newDeviceFiresOncePerDevice() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        NewDeviceRule rule = new NewDeviceRule();
        DetectionResult first = rule.evaluate(ctx("security.auth", "dev-user",
                "{\"eventType\":\"LOGIN_SUCCESS\",\"username\":\"dev-user\",\"deviceId\":\"device-A\"}", now));
        assertThat(first).isNotNull();
        assertComplete(first, NewDeviceRule.RULE_ID);
        assertThat(first.severity()).isEqualTo(Severity.MEDIUM);

        assertThat(rule.evaluate(ctx("security.auth", "dev-user",
                "{\"eventType\":\"LOGIN_SUCCESS\",\"username\":\"dev-user\",\"deviceId\":\"device-A\"}",
                now.plusSeconds(10)))).isNull();

        DetectionResult secondDevice = rule.evaluate(ctx("security.auth", "dev-user",
                "{\"eventType\":\"LOGIN_SUCCESS\",\"username\":\"dev-user\",\"deviceId\":\"device-B\"}",
                now.plusSeconds(20)));
        assertThat(secondDevice).isNotNull();
    }

    @Test
    @DisplayName("NEW_DEVICE ignores logins without a device id and failed logins")
    void newDeviceNegative() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        NewDeviceRule rule = new NewDeviceRule();
        assertThat(rule.evaluate(ctx("security.auth", "nod-dev",
                "{\"eventType\":\"LOGIN_SUCCESS\",\"username\":\"nod-dev\"}", now))).isNull();
        assertThat(rule.evaluate(ctx("security.auth", "nod-dev",
                "{\"eventType\":\"LOGIN_FAILED\",\"username\":\"nod-dev\",\"deviceId\":\"d1\"}", now))).isNull();
    }

    // ---------- NEW_IP ----------

    @Test
    @DisplayName("NEW_IP fires once per unseen IP, then stays silent")
    void newIpFiresOncePerIp() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        NewIpRule rule = new NewIpRule();
        DetectionResult first = rule.evaluate(ctx("security.auth", "ip-user",
                "{\"eventType\":\"LOGIN_SUCCESS\",\"username\":\"ip-user\",\"sourceIp\":\"203.0.113.9\"}", now));
        assertThat(first).isNotNull();
        assertComplete(first, NewIpRule.RULE_ID);
        assertThat(first.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(rule.evaluate(ctx("security.auth", "ip-user",
                "{\"eventType\":\"LOGIN_SUCCESS\",\"username\":\"ip-user\",\"sourceIp\":\"203.0.113.9\"}",
                now.plusSeconds(5)))).isNull();
        assertThat(rule.evaluate(ctx("security.auth", "ip-user",
                "{\"eventType\":\"LOGIN_SUCCESS\",\"username\":\"ip-user\",\"sourceIp\":\"198.51.100.7\"}",
                now.plusSeconds(10)))).isNotNull();
    }

    // ---------- UNUSUAL_TRANSACTION_AMOUNT ----------

    @Test
    @DisplayName("UNUSUAL_TRANSACTION_AMOUNT fires above the rolling baseline")
    void unusualAmountFiresAboveBaseline() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        for (int i = 0; i < 3; i++) {
            prior("security.payment", "cust-1", Map.of(
                    "eventType", "PAYMENT_CREATED", "customerId", "cust-1", "amount", 100), now.minusSeconds(60));
        }
        DetectionResult r = new UnusualTransactionAmountRule().evaluate(
                ctx("security.payment", "cust-1",
                        "{\"eventType\":\"PAYMENT_CREATED\",\"customerId\":\"cust-1\",\"amount\":600}", now));
        assertThat(r).isNotNull();
        assertComplete(r, UnusualTransactionAmountRule.RULE_ID);
        assertThat(r.severity()).isEqualTo(Severity.HIGH);
        assertThat(r.reason()).contains("600");
    }

    @Test
    @DisplayName("UNUSUAL_TRANSACTION_AMOUNT fires on the absolute ceiling without history")
    void unusualAmountFiresOnCeiling() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        DetectionResult r = new UnusualTransactionAmountRule().evaluate(
                ctx("security.payment", "cust-2",
                        "{\"eventType\":\"PAYMENT_CREATED\",\"customerId\":\"cust-2\",\"amount\":25000}", now));
        assertThat(r).isNotNull();
        assertThat(r.reason()).contains("ceiling");
    }

    @Test
    @DisplayName("UNUSUAL_TRANSACTION_AMOUNT stays silent for normal amounts and small history")
    void unusualAmountNegative() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        for (int i = 0; i < 3; i++) {
            prior("security.payment", "cust-3", Map.of(
                    "eventType", "PAYMENT_CREATED", "customerId", "cust-3", "amount", 100), now.minusSeconds(60));
        }
        assertThat(new UnusualTransactionAmountRule().evaluate(ctx("security.payment", "cust-3",
                "{\"eventType\":\"PAYMENT_CREATED\",\"customerId\":\"cust-3\",\"amount\":120}", now))).isNull();
        // only one baseline payment: not enough history
        prior("security.payment", "cust-4", Map.of(
                "eventType", "PAYMENT_CREATED", "customerId", "cust-4", "amount", 100), now.minusSeconds(60));
        assertThat(new UnusualTransactionAmountRule().evaluate(ctx("security.payment", "cust-4",
                "{\"eventType\":\"PAYMENT_CREATED\",\"customerId\":\"cust-4\",\"amount\":600}", now))).isNull();
        assertThat(new UnusualTransactionAmountRule().evaluate(ctx("security.payment", "cust-3",
                "{\"eventType\":\"PAYMENT_CREATED\",\"customerId\":\"cust-3\"}", now))).isNull();
    }

    // ---------- TRANSACTION_VELOCITY ----------

    @Test
    @DisplayName("TRANSACTION_VELOCITY fires at 5 payments in 1 minute")
    void velocityFires() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        for (int i = 0; i < 4; i++) {
            prior("security.payment", "vel-cust", Map.of(
                    "eventType", "PAYMENT_CREATED", "customerId", "vel-cust", "amount", 10), now.minusSeconds(i));
        }
        DetectionResult r = new TransactionVelocityRule().evaluate(
                ctx("security.payment", "vel-cust",
                        "{\"eventType\":\"PAYMENT_CREATED\",\"customerId\":\"vel-cust\",\"amount\":10}", now));
        assertThat(r).isNotNull();
        assertComplete(r, TransactionVelocityRule.RULE_ID);
        assertThat(r.reason()).contains("5 payments");
    }

    @Test
    @DisplayName("TRANSACTION_VELOCITY stays silent below threshold and outside window")
    void velocityNegative() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        for (int i = 0; i < 3; i++) {
            prior("security.payment", "slow-cust", Map.of(
                    "eventType", "PAYMENT_CREATED", "customerId", "slow-cust", "amount", 10), now.minusSeconds(i));
        }
        assertThat(new TransactionVelocityRule().evaluate(ctx("security.payment", "slow-cust",
                "{\"eventType\":\"PAYMENT_CREATED\",\"customerId\":\"slow-cust\",\"amount\":10}", now))).isNull();
        // payments older than 1 minute don't count
        for (int i = 1; i <= 4; i++) {
            prior("security.payment", "old-cust", Map.of(
                    "eventType", "PAYMENT_CREATED", "customerId", "old-cust", "amount", 10),
                    now.minusSeconds(120));
        }
        assertThat(new TransactionVelocityRule().evaluate(ctx("security.payment", "old-cust",
                "{\"eventType\":\"PAYMENT_CREATED\",\"customerId\":\"old-cust\",\"amount\":10}", now))).isNull();
    }

    // ---------- MULTIPLE_FAILED_PAYMENTS ----------

    @Test
    @DisplayName("MULTIPLE_FAILED_PAYMENTS fires at 3 declined payments in 5 minutes")
    void multipleFailedPaymentsFires() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        for (int i = 0; i < 2; i++) {
            prior("security.payment", "fail-cust", Map.of(
                    "eventType", "PAYMENT_CREATED", "customerId", "fail-cust", "status", "DECLINED"),
                    now.minusSeconds(30));
        }
        DetectionResult r = new MultipleFailedPaymentsRule().evaluate(
                ctx("security.payment", "fail-cust",
                        "{\"eventType\":\"PAYMENT_CREATED\",\"customerId\":\"fail-cust\",\"status\":\"DECLINED\"}", now));
        assertThat(r).isNotNull();
        assertComplete(r, MultipleFailedPaymentsRule.RULE_ID);
        assertThat(r.reason()).contains("3 failed payments");
    }

    @Test
    @DisplayName("MULTIPLE_FAILED_PAYMENTS stays silent below threshold and for successful payments")
    void multipleFailedPaymentsNegative() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        prior("security.payment", "ok-cust", Map.of(
                "eventType", "PAYMENT_CREATED", "customerId", "ok-cust", "status", "DECLINED"), now.minusSeconds(30));
        assertThat(new MultipleFailedPaymentsRule().evaluate(ctx("security.payment", "ok-cust",
                "{\"eventType\":\"PAYMENT_CREATED\",\"customerId\":\"ok-cust\",\"status\":\"DECLINED\"}", now)))
                .isNull();
        assertThat(new MultipleFailedPaymentsRule().evaluate(ctx("security.payment", "ok-cust",
                "{\"eventType\":\"PAYMENT_CREATED\",\"customerId\":\"ok-cust\",\"status\":\"COMPLETED\"}", now)))
                .isNull();
    }

    // ---------- API_REQUEST_SPIKE ----------

    @Test
    @DisplayName("API_REQUEST_SPIKE fires at 100 requests per IP in 1 minute")
    void apiRequestSpikeFires() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        for (int i = 0; i < 99; i++) {
            prior("security.api", "10.0.0.1", Map.of(
                    "eventType", "API_REQUEST", "sourceIp", "10.0.0.1", "path", "/api/x"), now.minusSeconds(1));
        }
        DetectionResult r = new ApiRequestSpikeRule().evaluate(
                ctx("security.api", "10.0.0.1",
                        "{\"eventType\":\"API_REQUEST\",\"sourceIp\":\"10.0.0.1\",\"path\":\"/api/x\"}", now));
        assertThat(r).isNotNull();
        assertComplete(r, ApiRequestSpikeRule.RULE_ID);
        assertThat(r.reason()).contains("100 API requests");
    }

    @Test
    @DisplayName("API_REQUEST_SPIKE stays silent below threshold")
    void apiRequestSpikeNegative() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        for (int i = 0; i < 50; i++) {
            prior("security.api", "10.0.0.2", Map.of(
                    "eventType", "API_REQUEST", "sourceIp", "10.0.0.2", "path", "/api/x"), now.minusSeconds(1));
        }
        assertThat(new ApiRequestSpikeRule().evaluate(ctx("security.api", "10.0.0.2",
                "{\"eventType\":\"API_REQUEST\",\"sourceIp\":\"10.0.0.2\",\"path\":\"/api/x\"}", now))).isNull();
    }

    // ---------- BOT_ACTIVITY ----------

    @Test
    @DisplayName("BOT_ACTIVITY fires on a bot user-agent")
    void botActivityFiresOnUserAgent() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        DetectionResult r = new BotActivityRule().evaluate(
                ctx("security.api", "10.0.0.3",
                        "{\"eventType\":\"API_REQUEST\",\"sourceIp\":\"10.0.0.3\",\"path\":\"/api/x\","
                                + "\"userAgent\":\"python-requests/2.31\"}", now));
        assertThat(r).isNotNull();
        assertComplete(r, BotActivityRule.RULE_ID);
        assertThat(r.reason()).contains("python-requests/2.31");
    }

    @Test
    @DisplayName("BOT_ACTIVITY stays silent for normal browsers")
    void botActivityNegative() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        assertThat(new BotActivityRule().evaluate(ctx("security.api", "10.0.0.5",
                "{\"eventType\":\"API_REQUEST\",\"sourceIp\":\"10.0.0.5\",\"path\":\"/api/x\","
                        + "\"userAgent\":\"Mozilla/5.0 (Windows NT 10.0) Chrome/126\"}", now))).isNull();
    }

    // ---------- PORT_SCAN ----------

    @Test
    @DisplayName("PORT_SCAN fires at 10 distinct destination ports in 1 minute")
    void portScanFires() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        for (int i = 0; i < 9; i++) {
            prior("security.network", "192.0.2.1", Map.of(
                    "eventType", "CONNECTION", "sourceIp", "192.0.2.1", "destinationPort", 1000 + i),
                    now.minusSeconds(1));
        }
        DetectionResult r = new PortScanRule().evaluate(
                ctx("security.network", "192.0.2.1",
                        "{\"eventType\":\"CONNECTION\",\"sourceIp\":\"192.0.2.1\",\"destinationPort\":1099}", now));
        assertThat(r).isNotNull();
        assertComplete(r, PortScanRule.RULE_ID);
        assertThat(r.severity()).isEqualTo(Severity.HIGH);
        assertThat(r.reason()).contains("10 distinct destination ports");
    }

    @Test
    @DisplayName("PORT_SCAN stays silent below threshold and for repeated ports")
    void portScanNegative() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        for (int i = 0; i < 8; i++) {
            prior("security.network", "192.0.2.2", Map.of(
                    "eventType", "CONNECTION", "sourceIp", "192.0.2.2", "destinationPort", 1000 + i),
                    now.minusSeconds(1));
        }
        assertThat(new PortScanRule().evaluate(ctx("security.network", "192.0.2.2",
                "{\"eventType\":\"CONNECTION\",\"sourceIp\":\"192.0.2.2\",\"destinationPort\":1088}", now))).isNull();
        // repeated single port: many events, one distinct port
        for (int i = 0; i < 20; i++) {
            prior("security.network", "192.0.2.3", Map.of(
                    "eventType", "CONNECTION", "sourceIp", "192.0.2.3", "destinationPort", 443),
                    now.minusSeconds(1));
        }
        assertThat(new PortScanRule().evaluate(ctx("security.network", "192.0.2.3",
                "{\"eventType\":\"CONNECTION\",\"sourceIp\":\"192.0.2.3\",\"destinationPort\":443}", now))).isNull();
    }

    // ---------- CONNECTION_SPIKE ----------

    @Test
    @DisplayName("CONNECTION_SPIKE fires at 50 connections per IP in 1 minute")
    void connectionSpikeFires() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        for (int i = 0; i < 49; i++) {
            prior("security.network", "192.0.2.9", Map.of(
                    "eventType", "CONNECTION", "sourceIp", "192.0.2.9"), now.minusSeconds(1));
        }
        DetectionResult r = new ConnectionSpikeRule().evaluate(
                ctx("security.network", "192.0.2.9",
                        "{\"eventType\":\"CONNECTION\",\"sourceIp\":\"192.0.2.9\"}", now));
        assertThat(r).isNotNull();
        assertComplete(r, ConnectionSpikeRule.RULE_ID);
        assertThat(r.reason()).contains("50 network connections");
    }

    @Test
    @DisplayName("CONNECTION_SPIKE stays silent below threshold")
    void connectionSpikeNegative() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        for (int i = 0; i < 20; i++) {
            prior("security.network", "192.0.2.10", Map.of(
                    "eventType", "CONNECTION", "sourceIp", "192.0.2.10"), now.minusSeconds(1));
        }
        assertThat(new ConnectionSpikeRule().evaluate(ctx("security.network", "192.0.2.10",
                "{\"eventType\":\"CONNECTION\",\"sourceIp\":\"192.0.2.10\"}", now))).isNull();
    }

    // ---------- SUSPICIOUS_OUTBOUND ----------

    @Test
    @DisplayName("SUSPICIOUS_OUTBOUND fires on large transfers and external destinations")
    void suspiciousOutboundFires() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        DetectionResult big = new SuspiciousOutboundRule().evaluate(
                ctx("security.network", "10.1.0.1",
                        "{\"eventType\":\"OUTBOUND_TRANSFER\",\"sourceIp\":\"10.1.0.1\","
                                + "\"direction\":\"outbound\",\"bytes\":2500000000}", now));
        assertThat(big).isNotNull();
        assertComplete(big, SuspiciousOutboundRule.RULE_ID);
        assertThat(big.reason()).contains("outbound transfer");

        DetectionResult external = new SuspiciousOutboundRule().evaluate(
                ctx("security.network", "10.1.0.2",
                        "{\"eventType\":\"CONNECTION\",\"sourceIp\":\"10.1.0.2\","
                                + "\"direction\":\"outbound\",\"destinationExternal\":\"true\",\"bytes\":1000}", now));
        assertThat(external).isNotNull();
        assertThat(external.reason()).contains("external destination");
    }

    @Test
    @DisplayName("SUSPICIOUS_OUTBOUND stays silent for small internal transfers")
    void suspiciousOutboundNegative() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        assertThat(new SuspiciousOutboundRule().evaluate(ctx("security.network", "10.1.0.3",
                "{\"eventType\":\"OUTBOUND_TRANSFER\",\"sourceIp\":\"10.1.0.3\","
                        + "\"direction\":\"outbound\",\"bytes\":1000}", now))).isNull();
        assertThat(new SuspiciousOutboundRule().evaluate(ctx("security.network", "10.1.0.3",
                "{\"eventType\":\"CONNECTION\",\"sourceIp\":\"10.1.0.3\","
                        + "\"direction\":\"inbound\",\"bytes\":9999999999}", now))).isNull();
    }

    // ---------- UNAUTHORIZED_DATA_ACCESS ----------

    @Test
    @DisplayName("UNAUTHORIZED_DATA_ACCESS fires on denied access and failed exports")
    void unauthorizedDataAccessFires() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        DetectionResult denied = new UnauthorizedDataAccessRule().evaluate(
                ctx("security.audit", "analyst-1",
                        "{\"eventType\":\"RECORD_READ\",\"outcome\":\"DENIED\",\"username\":\"analyst-1\"}", now));
        assertThat(denied).isNotNull();
        assertComplete(denied, UnauthorizedDataAccessRule.RULE_ID);
        assertThat(denied.severity()).isEqualTo(Severity.HIGH);

        DetectionResult failedExport = new UnauthorizedDataAccessRule().evaluate(
                ctx("security.api", "analyst-1",
                        "{\"eventType\":\"DATA_EXPORT\",\"outcome\":\"FAILURE\",\"username\":\"analyst-1\"}", now));
        assertThat(failedExport).isNotNull();
        assertThat(failedExport.reason()).contains("DATA_EXPORT");
    }

    @Test
    @DisplayName("UNAUTHORIZED_DATA_ACCESS stays silent for successful non-data actions")
    void unauthorizedDataAccessNegative() {
        Instant now = Instant.parse("2026-09-03T10:00:00Z");
        assertThat(new UnauthorizedDataAccessRule().evaluate(ctx("security.audit", "analyst-2",
                "{\"eventType\":\"RECORD_READ\",\"outcome\":\"SUCCESS\",\"username\":\"analyst-2\"}", now))).isNull();
        assertThat(new UnauthorizedDataAccessRule().evaluate(ctx("security.audit", "analyst-2",
                "{\"eventType\":\"DATA_EXPORT\",\"outcome\":\"SUCCESS\",\"username\":\"analyst-2\"}", now))).isNull();
        assertThat(new UnauthorizedDataAccessRule().evaluate(ctx("security.audit", "analyst-2",
                "{\"eventType\":\"AUDIT_ENTRY\",\"username\":\"analyst-2\"}", now))).isNull();
    }

    // ---------- PRIVILEGED_ACCESS_ANOMALY ----------

    @Test
    @DisplayName("PRIVILEGED_ACCESS_ANOMALY escalates to CRITICAL off-hours")
    void privilegedAccessAnomalyOffHours() {
        // 23:30 UTC is outside 07:00-20:00
        Instant night = Instant.parse("2026-09-03T23:30:00Z");
        DetectionResult r = new PrivilegedAccessAnomalyRule().evaluate(
                ctx("security.audit", "admin-1",
                        "{\"eventType\":\"ROLE_CHANGE\",\"username\":\"admin-1\",\"targetRole\":\"ADMIN\"}", night));
        assertThat(r).isNotNull();
        assertComplete(r, PrivilegedAccessAnomalyRule.RULE_ID);
        assertThat(r.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(r.riskContribution()).isEqualTo(45);
        assertThat(r.reason()).contains("privileged action");
    }

    @Test
    @DisplayName("PRIVILEGED_ACCESS_ANOMALY stays silent for ordinary actions")
    void privilegedAccessAnomalyNegative() {
        Instant noon = Instant.parse("2026-09-03T12:00:00Z");
        assertThat(new PrivilegedAccessAnomalyRule().evaluate(ctx("security.audit", "user-1",
                "{\"eventType\":\"AUDIT_ENTRY\",\"username\":\"user-1\"}", noon))).isNull();
        assertThat(new PrivilegedAccessAnomalyRule().evaluate(ctx("security.audit", "user-1",
                "{\"eventType\":\"LOGIN_SUCCESS\",\"username\":\"user-1\"}", noon))).isNull();
    }

    // ---------- context plumbing ----------

    @Test
    @DisplayName("resolveOccurredAt prefers payload timestamps and falls back")
    void resolveOccurredAtPrefersPayload() {
        Instant fallback = Instant.parse("2026-09-03T09:00:00Z");
        var payload = MAPPER.createObjectNode();
        assertThat(DetectionContext.resolveOccurredAt(payload, fallback)).isEqualTo(fallback);
        payload.put("occurredAt", "2026-09-03T08:00:00Z");
        assertThat(DetectionContext.resolveOccurredAt(payload, fallback))
                .isEqualTo(Instant.parse("2026-09-03T08:00:00Z"));
    }

    @Test
    @DisplayName("subjectKey falls back through username, key and sourceIp")
    void subjectKeyFallbacks() {
        DetectionContext keyed = new DetectionContext("security.api", "key-1", "c",
                MAPPER.createObjectNode(), Instant.now(), windows);
        assertThat(keyed.subjectKey()).isEqualTo("key-1");
        DetectionContext byIp = new DetectionContext("security.api", null, "c",
                MAPPER.createObjectNode().put("sourceIp", "10.9.9.9"), Instant.now(), windows);
        assertThat(byIp.subjectKey()).isEqualTo("10.9.9.9");
    }
}
