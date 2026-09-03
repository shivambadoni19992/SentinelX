package com.sentinelx.retail.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tunables for the retail commerce flow. */
@ConfigurationProperties(prefix = "sentinelx.retail")
public record RetailProperties(
        /** Kafka topic for CHECKOUT_STARTED / CHECKOUT_FAILED / ORDER_CREATED events. */
        String eventTopic) {

    public RetailProperties {
        if (eventTopic == null || eventTopic.isBlank()) {
            eventTopic = "sentinelx.retail.events";
        }
    }
}