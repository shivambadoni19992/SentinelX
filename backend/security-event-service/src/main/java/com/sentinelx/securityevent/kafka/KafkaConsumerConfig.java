package com.sentinelx.securityevent.kafka;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer wiring for the security-event-service normalizer.
 *
 * <p>Consumers run in the {@code sentinelx-security-event-normalizer} group.
 * Processing failures are retried 3 times with a 1 second backoff, then the
 * offending record is published to the paired {@code security.<domain>.dlt}
 * dead-letter topic and the consumer moves on — one poison message can never
 * stall a partition.
 */
@Configuration
public class KafkaConsumerConfig {

    public static final String GROUP_ID = "sentinelx-security-event-normalizer";
    public static final String HEADER_CORRELATION_ID = "correlationId";
    public static final String MDC_CORRELATION_ID = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    /**
     * Recoverer that publishes exhausted records to {@code <topic>.dlt} on the
     * same partition as the original record, preserving the original payload,
     * key and headers, plus a {@link DeadLetterPublishingRecoverer} exception
     * header describing why the record was dead-lettered.
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                // keep the record on its original partition in the DLT topic
                (record, ex) -> new TopicPartition(record.topic() + ".dlt", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return recoverer;
    }

    /** Retry 3 times (1s apart), then dead-letter via the recoverer. */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler errorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    /** Promotes the {@code correlationId} header/payload to the logging MDC. */
    public static String promoteCorrelationId(ConsumerRecord<String, String> record, String payloadFallback) {
        String correlationId = null;
        Header header = record.headers().lastHeader(HEADER_CORRELATION_ID);
        if (header != null && header.value() != null) {
            correlationId = new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
        }
        if (correlationId == null || correlationId.isBlank() || "null".equals(correlationId)) {
            correlationId = payloadFallback;
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = record.key() == null ? null : record.key();
        }
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(MDC_CORRELATION_ID, correlationId);
        }
        return correlationId;
    }

    /** Copies all record headers into a string map for persistence in metadata. */
    public static Map<String, String> headersToMap(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new HashMap<>();
        for (Header h : record.headers()) {
            if (h.value() != null) {
                headers.put(h.key(), new String(h.value(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return headers;
    }

    static {
        // Guard against misuse: clear MDC helper for completeness in tests.
        log.debug("KafkaConsumerConfig loaded");
    }
}
