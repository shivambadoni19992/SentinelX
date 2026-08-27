package com.sentinelx.retail.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.sentinelx.retail.entity.Order;

/** API contract for an Order. */
public record OrderDto(
        UUID id,
        UUID userId,
        String status,
        BigDecimal totalAmount,
        String currency,
        Instant placedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderDto from(Order o) {
        return new OrderDto(o.getId(), o.getUserId(), o.getStatus(), o.getTotalAmount(),
                o.getCurrency(), o.getPlacedAt(), o.getCreatedAt(), o.getUpdatedAt());
    }

    public Order toEntity() {
        Order o = new Order();
        o.setUserId(userId);
        o.setStatus(status == null ? "PENDING" : status);
        o.setTotalAmount(totalAmount);
        o.setCurrency(currency == null ? "USD" : currency);
        o.setPlacedAt(placedAt);
        return o;
    }
}