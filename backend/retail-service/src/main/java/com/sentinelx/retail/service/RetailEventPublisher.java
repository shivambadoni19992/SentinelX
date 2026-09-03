package com.sentinelx.retail.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes retail commerce events ({@code CHECKOUT_STARTED},
 * {@code CHECKOUT_FAILED}, {@code ORDER_CREATED}) to Kafka.
 *
 * <p>Publishing is best-effort by design: a Kafka outage must not fail the
 * request that has already been processed, so send failures are logged rather
 * than thrown. The message key is the order id (or cart owner id), keeping a
 * subject's events ordered within a partition.
 */
@Component
public class RetailEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RetailEventPublisher.class);

    public static final String CHECKOUT_STARTED = "CHECKOUT_STARTED";
    public static final String CHECKOUT_FAILED = "CHECKOUT_FAILED";
    public static final String ORDER_CREATED = "ORDER_CREATED";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RetailProperties properties;

    public RetailEventPublisher(KafkaTemplate<String, String> kafkaTemplate, RetailProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    /** Serializes and publishes the event; never throws. */
    public void publish(String eventType, UUID key, Map<String, Object> extra) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", eventType);
            payload.put("correlationId", MDC.get("correlationId"));
            if (extra != null) {
                payload.putAll(extra);
            }
            String topic = properties.eventTopic();
            kafkaTemplate.send(topic, key.toString(), Json.write(payload));
            log.info("event published type={} topic={} key={} correlationId={}",
                    eventType, topic, key, MDC.get("correlationId"));
        } catch (Exception e) {
            log.warn("failed to publish {} for key={} — {}", eventType, key, e.getMessage());
        }
    }

    public void checkoutStarted(UUID userId, int itemCount) {
        publish(CHECKOUT_STARTED, userId, Map.of("userId", userId.toString(), "itemCount", itemCount));
    }

    public void checkoutFailed(UUID userId, String reason) {
        publish(CHECKOUT_FAILED, userId, Map.of("userId", userId.toString(), "reason", reason));
    }

    public void orderCreated(UUID orderId, UUID userId, String totalAmount, String currency, int itemCount) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("orderId", orderId.toString());
        extra.put("userId", userId.toString());
        extra.put("totalAmount", totalAmount);
        extra.put("currency", currency);
        extra.put("itemCount", itemCount);
        publish(ORDER_CREATED, orderId, extra);
    }

    public String topic() {
        return properties.eventTopic();
    }

    /** Minimal JSON writer to keep the event payload dependency-free and ordered. */
    static final class Json {
        private Json() {
        }

        static String write(Map<String, Object> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(quote(e.getKey())).append(':').append(value(e.getValue()));
            }
            return sb.append('}').toString();
        }

        private static String value(Object v) {
            if (v == null) {
                return "null";
            }
            if (v instanceof Number || v instanceof Boolean) {
                return v.toString();
            }
            return quote(v.toString());
        }

        private static String quote(String s) {
            return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
    }
}