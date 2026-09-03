package com.sentinelx.payment.entity;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A synthetic payment in the processing pipeline (schema payments.payments).
 *
 * <p>Contract fields: {@code paymentId, customerId, merchantId, amount,
 * currency, deviceId, ipAddress, status, createdAt}. Device and IP are stored
 * in full for fraud analysis but are always masked before leaving the service
 * (see {@code DataMasker}).
 */
@Entity
@Table(name = "payments", schema = "payments",
        uniqueConstraints = { @UniqueConstraint(name = "uq_payments_idempotency_key", columnNames = "idempotency_key") },
        indexes = {
                @Index(name = "idx_payments_customer_id", columnList = "customer_id"),
                @Index(name = "idx_payments_merchant_id", columnList = "merchant_id"),
                @Index(name = "idx_payments_status", columnList = "status")
        })
public class Payment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** Synthetic lifecycle status; never accepted from the client. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Client-supplied idempotency key (unique). Null when the client omits it. */
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    /** Internal synthetic decision reason; logged/queued, never returned by the API. */
    @Column(name = "decision_reason", length = 128)
    private String decisionReason;

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }
}