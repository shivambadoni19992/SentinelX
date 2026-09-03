package com.sentinelx.securityevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sentinelx.securityevent.entity.SecurityEvent;
import com.sentinelx.securityevent.kafka.KafkaTopics;
import com.sentinelx.securityevent.repository.SecurityEventRepository;

/**
 * End-to-end Kafka test: publishes JSON events to the real embedded broker on
 * the {@code security.*} topics and asserts the normalizing consumer group
 * persists normalized {@link SecurityEvent} rows.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {
        KafkaTopics.AUTH, KafkaTopics.PAYMENT, KafkaTopics.API, KafkaTopics.RETAIL,
        KafkaTopics.NETWORK, KafkaTopics.RISK, KafkaTopics.ALERT, KafkaTopics.AUDIT,
        KafkaTopics.AUTH + ".dlt", KafkaTopics.API + ".dlt"
})
@Testcontainers
class SecurityEventKafkaFlowTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
        registry.add("logging.level.org.springframework.kafka.listener", () -> "DEBUG");
        registry.add("logging.level.com.sentinelx.securityevent.kafka", () -> "DEBUG");
    }

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    org.springframework.kafka.core.ConsumerFactory<String, String> consumerFactory;

    @Autowired
    org.springframework.kafka.config.KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    SecurityEventRepository events;

    @BeforeAll
    static void debugLogging() {
        var root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.springframework.kafka");
        root.setLevel(ch.qos.logback.classic.Level.DEBUG);
    }

    private void send(String topic, String key, String payload) {
        try {
            var result = kafka.send(topic, key, payload).get(10, java.util.concurrent.TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertNotNull(result.getRecordMetadata());
        } catch (Exception e) {
            throw new IllegalStateException("send to " + topic + " failed", e);
        }
    }

    @Test
    void normalizesEventsFromEverySecurityTopic() {
        String correlationId = "it-" + UUID.randomUUID();
        String paymentId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();

        send(KafkaTopics.AUTH, customerId,
                "{\"eventType\":\"LOGIN_SUCCESS\",\"userId\":\"" + customerId + "\",\"username\":\"alice\","
                        + "\"outcome\":\"SUCCESS\",\"severity\":\"LOW\",\"correlationId\":\"" + correlationId + "\"}");
        send(KafkaTopics.PAYMENT, paymentId,
                "{\"eventType\":\"PAYMENT_CREATED\",\"paymentId\":\"" + paymentId + "\",\"customerId\":\"" + customerId
                        + "\",\"amount\":42.50,\"currency\":\"USD\",\"status\":\"COMPLETED\",\"correlationId\":\""
                        + correlationId + "\"}");
        send(KafkaTopics.RETAIL, paymentId,
                "{\"eventType\":\"ORDER_CREATED\",\"orderId\":\"" + paymentId + "\",\"userId\":\"" + customerId
                        + "\",\"totalAmount\":\"42.50\",\"correlationId\":\"" + correlationId + "\"}");
        send(KafkaTopics.AUDIT, customerId,
                "{\"eventType\":\"AUDIT_ENTRY\",\"actor\":\"alice\",\"action\":\"EXPORT\","
                        + "\"correlationId\":\"" + correlationId + "\"}");
        send(KafkaTopics.ALERT, customerId,
                "{\"eventType\":\"ALERT_RAISED\",\"severity\":\"CRITICAL\",\"correlationId\":\"" + correlationId + "\"}");

        System.out.println("KAFKA-FLOW consumerConfig=" + consumerFactory.getConfigurationProperties());

        try (var probe = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(
                java.util.Map.of(
                        org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        System.getProperty("spring.embedded.kafka.brokers"),
                        org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                        org.apache.kafka.common.serialization.StringDeserializer.class.getName(),
                        org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                        org.apache.kafka.common.serialization.StringDeserializer.class.getName()))) {
            var endOffsets = probe.endOffsets(java.util.Set.of(
                    new org.apache.kafka.common.TopicPartition(KafkaTopics.AUTH, 0),
                    new org.apache.kafka.common.TopicPartition(KafkaTopics.PAYMENT, 0)));
            System.out.println("KAFKA-FLOW endOffsets=" + endOffsets);
            // Independent probe: assign manually and poll to confirm broker deliverability.
            probe.assign(java.util.Set.of(new org.apache.kafka.common.TopicPartition(KafkaTopics.AUTH, 0)));
            probe.seekToBeginning(java.util.Set.of(new org.apache.kafka.common.TopicPartition(KafkaTopics.AUTH, 0)));
            var polled = probe.poll(java.time.Duration.ofSeconds(10));
            System.out.println("KAFKA-FLOW probeRecords=" + polled.count());
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            System.out.println("KAFKA-FLOW containersRunning=" + listenerRegistry.getListenerContainers().stream()
                    .map(c -> c.isRunning() + "/" + c.isContainerPaused()).toList());
            var rows = events.findAll().stream()
                    .filter(e -> correlationId.equals(String.valueOf(e.getMetadata().get("correlationId"))))
                    .toList();
            assertThat(rows).hasSize(5);
            assertThat(rows).extracting(SecurityEvent::getEventType)
                    .contains("LOGIN_SUCCESS", "PAYMENT_CREATED", "ORDER_CREATED", "AUDIT_ENTRY", "ALERT_RAISED");
            // Normalization: payment COMPLETED -> SUCCESS, alert default severity CRITICAL
            var payment = rows.stream().filter(e -> e.getEventType().equals("PAYMENT_CREATED")).findFirst().orElseThrow();
            assertThat(payment.getOutcome()).isEqualTo("SUCCESS");
            assertThat(payment.getUserId()).isEqualTo(UUID.fromString(customerId));
            var alert = rows.stream().filter(e -> e.getEventType().equals("ALERT_RAISED")).findFirst().orElseThrow();
            assertThat(alert.getSeverity()).isEqualTo("CRITICAL");
            assertThat(alert.getMetadata()).containsEntry("sourceTopic", KafkaTopics.ALERT)
                    .containsKey("correlationId");
        });
    }

    @Test
    void deadLettersPoisonRecordAfterRetries() {
        // A non-JSON payload can never be normalized: after 3 retries it must
        // land on the paired security.api.dlt topic, not stall the partition.
        kafka.send(KafkaTopics.API, "poison", "not-json-at-all").join();

        var dlt = java.util.Set.of(new org.apache.kafka.common.TopicPartition(KafkaTopics.API + ".dlt", 0));
        try (var probe = new org.apache.kafka.clients.consumer.KafkaConsumer<String, String>(java.util.Map.of(
                org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                System.getProperty("spring.embedded.kafka.brokers"),
                org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class.getName(),
                org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class.getName(),
                org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, "dlt-probe"))) {
            probe.assign(dlt);
            probe.seekToBeginning(dlt);
            var records = probe.poll(Duration.ofSeconds(20));
            assertThat(records.count()).as("dead-lettered record present in " + dlt).isEqualTo(1);
        }
    }
}
