package com.sentinelx.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import com.sentinelx.alert.domain.AlertAction;
import com.sentinelx.alert.entity.SecurityAlert;
import com.sentinelx.alert.repository.SecurityAlertRepository;
import com.sentinelx.alert.service.AlertWorkflowService;
import com.sentinelx.alert.service.ResponseActionExecutor;

/**
 * End-to-end Kafka flow: a {@code RISK_DECIDED} event published to
 * {@code security.alert} opens a persisted alert, and applying a response
 * action emits an audit event on {@code security.audit}.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "sentinelx.response.payment-service-url=http://payment.test",
        "sentinelx.response.auth-service-url=http://auth.test",
        "sentinelx.response.rate-limit-ttl=PT15M",
        "sentinelx.response.verification-ttl=PT24H"})
@EmbeddedKafka(partitions = 1, topics = {"security.alert", "security.audit", "security.alert.dlt"})
class AlertKafkaFlowTest {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    EmbeddedKafkaBroker broker;

    @MockBean
    SecurityAlertRepository alerts;

    @MockBean
    ResponseActionExecutor executor;

    @Autowired
    AlertWorkflowService workflow;

    @Test
    void riskDecisionOpensAlertAndActionEmitsAudit() {
        Mockito.when(alerts.save(any(SecurityAlert.class)))
                .thenAnswer(inv -> inv.getArgument(0, SecurityAlert.class));

        String riskDecision = """
                {"eventType":"RISK_DECIDED","subject":"kafka-user",
                 "eventId":"%s","score":75,"level":"HIGH",
                 "action":"CHALLENGE",
                 "reasons":["failed logins (+20): 5 failed logins in 5 minutes",
                            "new device (+15): NEW_DEVICE — first sighting"],
                 "decidedAt":"2026-09-04T10:00:00Z"}""".formatted(UUID.randomUUID());

        kafkaTemplate.send("security.alert", "kafka-user", riskDecision);

        // alert opened with severity, status OPEN and explainable description
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                verify(alerts, atLeastOnce()).save(Mockito.argThat(a ->
                        a.getStatus().equals("OPEN")
                                && a.getSeverity().equals("HIGH")
                                && a.getTitle().contains("kafka-user")
                                && a.getDescription().contains("failed logins"))));

        // applying an action through the workflow emits an audit event
        SecurityAlert stored = new SecurityAlert();
        stored.setTitle("Risk HIGH — kafka-user");
        stored.setSeverity("HIGH");
        stored.setEntityType("RISK_SUBJECT");
        stored.setStatus("OPEN");
        Mockito.when(alerts.findById(any())).thenReturn(Optional.of(stored));
        Mockito.when(executor.apply(any(), any()))
                .thenReturn(Map.of("state", "APPLIED", "redisKey", "sentinelx:ratelimit:block:kafka-user"));

        workflow.applyAction(UUID.randomUUID(), AlertAction.RATE_LIMIT, "analyst");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<String> auditPayloads = readTopic("security.audit");
            assertThat(auditPayloads).anySatisfy(v -> {
                assertThat(v).contains("ALERT_ACTION_APPLIED");
                assertThat(v).contains("RATE_LIMIT");
                assertThat(v).contains("analyst");
            });
        });
    }

    private List<String> readTopic(String topic) {
        var props = KafkaTestUtils.consumerProps("verify-" + UUID.randomUUID(), "true", broker);
        props.put("key.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        props.put("value.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        var factory = new DefaultKafkaConsumerFactory<String, String>(props);
        try (var consumer = factory.createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, topic);
            var records = KafkaTestUtils.<String, String>getRecords(consumer).records(topic);
            List<String> out = new ArrayList<>();
            for (ConsumerRecord<String, String> r : records) {
                out.add(r.value());
            }
            return out;
        }
    }
}