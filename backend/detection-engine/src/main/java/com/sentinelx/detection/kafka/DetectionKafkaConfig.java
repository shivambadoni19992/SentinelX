package com.sentinelx.detection.kafka;

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
 * Kafka consumer wiring for the detection engine. Consumers run in the
 * {@code sentinelx-detection-engine} group. Processing failures are retried
 * twice with a 1 second backoff, then the record is dead-lettered to the
 * paired {@code security.<domain>.dlt} topic so a poison event can never
 * stall detection.
 */
@Configuration
public class DetectionKafkaConfig {

    public static final String GROUP_ID = "sentinelx-detection-engine";
    public static final String HEADER_CORRELATION_ID = "correlationId";
    public static final String MDC_CORRELATION_ID = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(DetectionKafkaConfig.class);

    @Bean
    public DeadLetterPublishingRecoverer detectionDeadLetterRecoverer(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (record, ex) -> new TopicPartition(record.topic() + ".dlt", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return recoverer;
    }

    /** Retry twice (1s apart), then dead-letter via the recoverer. */
    @Bean
    public DefaultErrorHandler detectionErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> detectionListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler detectionErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(detectionErrorHandler);
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    /**
     * Resolves the correlation id for a record: header first, then payload
     * field, then the record key, and promotes it into the MDC.
     */
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
            correlationId = record.key();
        }
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(MDC_CORRELATION_ID, correlationId);
        }
        return correlationId;
    }

    /** Copies all record headers into a string map. */
    public static Map<String, String> headersToMap(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new HashMap<>();
        for (Header h : record.headers()) {
            if (h.value() != null) {
                headers.put(h.key(), new String(h.value(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return headers;
    }
}
