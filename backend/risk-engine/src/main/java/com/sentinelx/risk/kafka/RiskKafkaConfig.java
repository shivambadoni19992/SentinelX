package com.sentinelx.risk.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka wiring for the risk engine. The engine consumes detections from
 * {@code security.risk} in the {@code sentinelx-risk-engine} group. Processing
 * failures retry twice (1s apart) and are then dead-lettered to the paired
 * {@code security.risk.dlt} topic.
 */
@Configuration
public class RiskKafkaConfig {

    public static final String GROUP_ID = "sentinelx-risk-engine";
    public static final String HEADER_CORRELATION_ID = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(RiskKafkaConfig.class);

    @Bean
    public DeadLetterPublishingRecoverer riskDeadLetterRecoverer(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (record, ex) -> new TopicPartition(record.topic() + ".dlt", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return recoverer;
    }

    @Bean
    public DefaultErrorHandler riskErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> riskListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler riskErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(riskErrorHandler);
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    /** Correlation id from a record header or the payload fallback. */
    public static String correlationId(ConsumerRecord<String, String> record, String payloadFallback) {
        Header header = record.headers().lastHeader(HEADER_CORRELATION_ID);
        if (header != null && header.value() != null) {
            String fromHeader = new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
            if (!fromHeader.isBlank() && !"null".equals(fromHeader)) {
                return fromHeader;
            }
        }
        return payloadFallback != null && !payloadFallback.isBlank() ? payloadFallback : record.key();
    }
}