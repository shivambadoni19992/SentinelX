package com.sentinelx.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

import com.sentinelx.risk.entity.RiskDecision;
import com.sentinelx.risk.repository.RiskDecisionRepository;

/**
 * End-to-end Kafka flow: a detection event published to {@code security.risk}
 * is consumed, scored, the decision persisted, and a {@code RISK_DECIDED}
 * event emitted on {@code security.alert}.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"})
@EmbeddedKafka(partitions = 1, topics = {"security.risk", "security.alert", "security.risk.dlt"})
class RiskKafkaFlowTest {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    EmbeddedKafkaBroker broker;

    @MockBean
    RiskDecisionRepository decisions;

    @Test
    void detectionFlowPersistsAndPublishes() {
        Mockito.when(decisions.save(any(RiskDecision.class)))
                .thenAnswer(inv -> inv.getArgument(0, RiskDecision.class));

        String detection = """
                {"eventType":"DETECTION_RAISED","ruleId":"FAILED_LOGIN_SPIKE",
                 "severity":"HIGH","riskContribution":30,
                 "reason":"5 failed logins in 5 minutes","recommendedAction":"CHALLENGE",
                 "sourceTopic":"security.auth","subject":"kafka-user",
                 "occurredAt":"2026-09-04T10:00:00Z","detectionId":"%s",
                 "correlationId":"corr-flow-1"}""".formatted(UUID.randomUUID());

        kafkaTemplate.send("security.risk", "FAILED_LOGIN_SPIKE", detection);

        // decision persisted with explainable factors
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                verify(decisions, atLeastOnce()).save(any(RiskDecision.class)));
        Mockito.verify(decisions, atLeastOnce()).save(Mockito.argThat(d ->
                "LOW".equals(d.getRiskLevel()) && d.getFactors().containsKey("reasons")));

        // risk event published downstream on security.alert
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<String> payloads = readAlerts();
            assertThat(payloads).anySatisfy(v -> {
                assertThat(v).contains("RISK_DECIDED");
                assertThat(v).contains("kafka-user");
                assertThat(v).contains("failed logins");
                assertThat(v).contains("ALLOW");
            });
        });
    }

    @Test
    void unknownRuleIdIsSkipped() {
        Mockito.when(decisions.save(any(RiskDecision.class)))
                .thenAnswer(inv -> inv.getArgument(0, RiskDecision.class));
        kafkaTemplate.send("security.risk", "UNKNOWN_RULE",
                "{\"eventType\":\"DETECTION_RAISED\",\"ruleId\":\"NOT_A_RULE\",\"subject\":\"x\"}");
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        Mockito.verify(decisions, Mockito.atMost(0)).save(any());
    }

    /** Consumes whatever RISK_DECIDED events have reached security.alert. */
    private List<String> readAlerts() {
        var props = KafkaTestUtils.consumerProps("alert-verify-" + UUID.randomUUID(), "true", broker);
        props.put("key.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        props.put("value.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        var consumerFactory = new DefaultKafkaConsumerFactory<String, String>(props);
        try (var consumer = consumerFactory.createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "security.alert");
            var records = KafkaTestUtils.<String, String>getRecords(consumer).records("security.alert");
            List<String> out = new ArrayList<>();
            for (ConsumerRecord<String, String> r : records) {
                out.add(r.value());
            }
            return out;
        }
    }
}