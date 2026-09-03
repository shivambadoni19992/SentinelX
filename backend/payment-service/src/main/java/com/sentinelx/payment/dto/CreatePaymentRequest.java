package com.sentinelx.payment.dto;

import java.util.UUID;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/payments}.
 *
 * <p>The client supplies merchant/device context; {@code status} is decided by
 * the service's synthetic processor and is intentionally not accepted here.
 * {@code idempotencyKey} may also be supplied via the {@code Idempotency-Key}
 * header (header wins when both are present).
 */
public record CreatePaymentRequest(
        @NotNull(message = "customerId is required")
        UUID customerId,

        @NotNull(message = "merchantId is required")
        UUID merchantId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        java.math.BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
        @Pattern(regexp = "USD|EUR|GBP", message = "currency must be one of USD, EUR, GBP")
        String currency,

        @Size(max = 64, message = "deviceId must be at most 64 characters")
        String deviceId,

        @Size(max = 45, message = "ipAddress must be at most 45 characters")
        @Pattern(
                regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^(?:[0-9A-Fa-f]{0,4}:){2,7}[0-9A-Fa-f]{0,4}$",
                message = "ipAddress must be a valid IPv4 or IPv6 address")
        String ipAddress,

        @Size(max = 128, message = "idempotencyKey must be at most 128 characters")
        String idempotencyKey) {
}