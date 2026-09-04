package com.sentinelx.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.detection.kafka.RecentDetections;

/**
 * End-to-end test on a real (embedded) Kafka broker: security events are
 * published as JSON to the security.* topics, the detection-engine consumer
 * group processes them, and the raised detections are asserted both on the
 * {@code security.risk} topic (via an independent probe consumer) and in the
 * in-memory recent-detections view.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {
        "security.auth", "security.payment", "security.api", "security.retail",
        "security.network", "security.risk", "security.alert", "security.audit",
        "security.auth.dlt", "security.payment.dlt", "security.api.dlt", "security.retail.dlt",
        "security.network.dlt", "security.risk.dlt", "security.alert.dlt", "security.audit.dlt"
})
class DetectionEngineKafkaFlowTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    RecentDetections recent;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers",
                () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    private void send(String topic, String key, String payload, String correlationId) {
        try {
            kafka.send(topic, key, payload).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("send to " + topic + " failed", e);
        }
    }

    private KafkaConsumer<String, String> probe(String topic) {
        var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("spring.embedded.kafka.brokers"),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class.getName(),
                ConsumerConfig.GROUP_ID_CONFIG, "flow-probe-" + topic));
        consumer.assign(Set.of(new TopicPartition(topic, 0)));
        consumer.seekToBeginning(Set.of(new TopicPartition(topic, 0)));
        return consumer;
    }

    @Test
    void detectsAuthSpikeFromRealKafkaTraffic() {
        String user = "flow-auth-" + Instant.now().toEpochMilli();
        String correlationId = "corr-flow-auth";

        // 5 failed logins -> FAILED_LOGIN_SPIKE must fire on the 5th
        for (int i = 0; i < 5; i++) {
            send("security.auth", user, "{\"eventType\":\"LOGIN_FAILED\",\"username\":\"" + user
                    + "\",\"sourceIp\":\"10.7.0.1\",\"occurredAt\":\"" + Instant.now()
                    + "\",\"correlationId\":\"" + correlationId + "\"}", correlationId);
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(recent.latest(50))
                    .anyMatch(d -> d.result().ruleId().equals("FAILED_LOGIN_SPIKE")
                            && user.equals(d.subject())
                            && correlationId.equals(d.correlationId()));
        });

        // The same detection must be consumable from security.risk as JSON with
        // every required field present.
        try (var consumer = probe("security.risk")) {
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                var records = consumer.poll(Duration.ofSeconds(2));
                boolean found = false;
                for (var record : records) {
                    JsonNode n = MAPPER.readTree(record.value());
                    if ("FAILED_LOGIN_SPIKE".equals(n.path("ruleId").asText())
                            && user.equals(n.path("subject").asText())) {
                        found = true;
                        assertThat(n.path("severity").asText()).isEqualTo("HIGH");
                        assertThat(n.path("riskContribution").asInt()).isEqualTo(35);
                        assertThat(n.path("reason").asText()).isNotBlank();
                        assertThat(n.path("recommendedAction").asText()).isNotBlank();
                        assertThat(n.path("eventType").asText()).isEqualTo("DETECTION_RAISED");
                        assertThat(n.path("correlationId").asText()).isEqualTo(correlationId);
                    }
                }
                assertThat(found).as("FAILED_LOGIN_SPIKE detection on security.risk").isTrue();
            });
        }
    }

    @Test
    void detectsPaymentVelocityFromRealKafkaTraffic() {
        String customer = "flow-cust-" + Instant.now().toEpochMilli();
        for (int i = 0; i < 5; i++) {
            send("security.payment", customer, "{\"eventType\":\"PAYMENT_CREATED\",\"customerId\":\""
                    + customer + "\",\"amount\":50,\"correlationId\":\"corr-velocity\"}", "corr-velocity");
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(recent.latest(50))
                    .anyMatch(d -> d.result().ruleId().equals("TRANSACTION_VELOCITY")
                            && customer.equals(d.subject()));
        });
    }

    @Test
    void detectsPortScanFromRealKafkaTraffic() {
        String ip = "10.9.9." + (Instant.now().getEpochSecond() % 200);
        for (int i = 0; i < 10; i++) {
            send("security.network", ip, "{\"eventType\":\"CONNECTION\",\"sourceIp\":\"" + ip
                    + "\",\"destinationPort\":" + (20000 + i) + ",\"correlationId\":\"corr-scan\"}", "corr-scan");
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(recent.latest(50))
                    .anyMatch(d -> d.result().ruleId().equals("PORT_SCAN") && ip.equals(d.subject()));
        });
    }

    @Test
    void poisonRecordIsDeadLettered() {
        kafka.send("security.api", "poison", "not-json-at-all").join();
        try (var consumer = probe("security.api.dlt")) {
            await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
                assertThat(consumer.poll(Duration.ofSeconds(5)).count())
                        .as("poison record dead-lettered to security.api.dlt")
                        .isGreaterThanOrEqualTo(1);
            });
        }
    }
}