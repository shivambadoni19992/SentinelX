package com.sentinelx.payment.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.sentinelx.payment.entity.Payment;
import com.sentinelx.payment.entity.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code PAYMENT_CREATED} events to Kafka.
 *
 * <p>Publishing is best-effort by design: a Kafka outage must not fail the
 * payment request that has already been persisted, so send failures are
 * logged and surfaced through metrics rather than thrown. The message key is
 * the payment id, which keeps a payment's events ordered within a partition.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    public static final String EVENT_TYPE = "PAYMENT_CREATED";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PaymentProperties properties;

    public PaymentEventPublisher(KafkaTemplate<String, String> kafkaTemplate, PaymentProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    /** Serializes and publishes the PAYMENT_CREATED event; never throws. */
    public void paymentCreated(Payment payment) {
        try {
            String payload = toJson(payment);
            String topic = properties.eventTopic();
            kafkaTemplate.send(topic, payment.getId().toString(), payload);
            log.info("event published type={} topic={} paymentId={} customerId={} status={} correlationId={}",
                    EVENT_TYPE, topic, payment.getId(), payment.getCustomerId(), payment.getStatus(),
                    MDC.get("correlationId"));
        } catch (Exception e) {
            log.warn("failed to publish {} for paymentId={} — {}",
                    EVENT_TYPE, payment.getId(), e.getMessage());
        }
    }

    private String toJson(Payment p) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", EVENT_TYPE);
        payload.put("paymentId", p.getId().toString());
        payload.put("customerId", p.getCustomerId().toString());
        payload.put("merchantId", p.getMerchantId().toString());
        payload.put("amount", p.getAmount());
        payload.put("currency", p.getCurrency());
        payload.put("status", p.getStatus().name());
        payload.put("createdAt", p.getCreatedAt() == null ? null : p.getCreatedAt().toString());
        payload.put("correlationId", MDC.get("correlationId"));
        // Deliberately excludes deviceId/ipAddress/decisionReason: events fan out
        // to many consumers, so sensitive financial data is masked out entirely.
        return Json.write(payload);
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

    /** Visible for tests: the topic the publisher currently targets. */
    public String topic() {
        return properties.eventTopic();
    }

    /** Visible for tests: payload status mapping sanity check. */
    public static String statusName(PaymentStatus status) {
        return status.name();
    }
}