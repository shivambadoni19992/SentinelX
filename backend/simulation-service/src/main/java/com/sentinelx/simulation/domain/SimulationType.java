package com.sentinelx.simulation.domain;

/**
 * The attack / behaviour scenarios a simulation can replay into the real
 * security pipeline. Each type maps onto one or more Kafka event topics and a
 * realistic event mix (see SimulatedEventFactory).
 */
public enum SimulationType {

    /** Benign baseline traffic — should produce near-zero detections. */
    NORMAL_TRAFFIC,
    /** Credential stuffing / repeated failed logins on few accounts. */
    BRUTE_FORCE,
    /** Compromise: unusual login success followed by privileged actions. */
    ACCOUNT_TAKEOVER,
    /** High-risk payment authorizations and declines. */
    PAYMENT_FRAUD,
    /** Many transactions from the same card/user in a short window. */
    TRANSACTION_VELOCITY,
    /** 4xx/429-heavy abusive API request patterns. */
    API_ABUSE,
    /** Non-human, high-volume, uniform request patterns. */
    BOT_ACTIVITY,
    /** Logins from unusual geos / times that succeed. */
    SUSPICIOUS_LOGIN,
    /** First-seen device fingerprints logging in. */
    NEW_DEVICE,
    /** Traffic from anonymizer / known-bad source IPs. */
    SUSPICIOUS_IP,
    /** Bursts of declined payment authorizations. */
    FAILED_PAYMENTS,
    /** Rapid cart mutation / checkout hammering. */
    CHECKOUT_ABUSE,
    /** Massive product/inventory view scraping. */
    INVENTORY_SCRAPING,
    /** Repeated coupon redemption across accounts. */
    COUPON_ABUSE,
    /** Sequential connection attempts across many ports. */
    PORT_SCAN,
    /** Sudden connection-count spikes against one host. */
    CONNECTION_SPIKE,
    /** Large outbound data transfers to unusual destinations. */
    SUSPICIOUS_OUTBOUND,
    /** Denied access attempts against protected data endpoints. */
    UNAUTHORIZED_DATA_ACCESS,
    /** Privileged/admin actions outside expected patterns. */
    PRIVILEGED_ACCESS_ANOMALY,
    /** A blended, multi-vector attack rehearsal. */
    MIXED_ATTACK;

    /** Whether this scenario injects hostile traffic (vs. benign baseline). */
    public boolean isAttack() {
        return this != NORMAL_TRAFFIC;
    }
}
