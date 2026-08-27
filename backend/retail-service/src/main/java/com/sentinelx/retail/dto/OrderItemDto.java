package com.sentinelx.retail.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.sentinelx.retail.entity.OrderItem;

/** API contract for an OrderItem. */
public record OrderItemDto(
        UUID id,
        UUID orderId,
        UUID productId,
        String productSku,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        Instant createdAt) {

    public static OrderItemDto from(OrderItem i) {
        return new OrderItemDto(i.getId(), i.getOrderId(), i.getProductId(), i.getProductSku(),
                i.getUnitPrice(), i.getQuantity(), i.getLineTotal(), i.getCreatedAt());
    }

    public OrderItem toEntity() {
        OrderItem i = new OrderItem();
        i.setOrderId(orderId);
        i.setProductId(productId);
        i.setProductSku(productSku);
        i.setUnitPrice(unitPrice);
        i.setQuantity(quantity);
        i.setLineTotal(lineTotal);
        return i;
    }
}