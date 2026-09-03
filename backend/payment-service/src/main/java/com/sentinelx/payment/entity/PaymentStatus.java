package com.sentinelx.payment.entity;

/** Lifecycle of a synthetic payment. Decided by the service, never by the client. */
public enum PaymentStatus {
    PENDING,
    APPROVED,
    HELD,
    DECLINED
}