package com.sentinelx.retail.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.sentinelx.retail.entity.Product;

/** API contract for a Product — no entity crosses the HTTP boundary. */
public record ProductDto(
        UUID id,
        String sku,
        String name,
        String description,
        String category,
        BigDecimal price,
        String currency,
        int stock,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductDto from(Product p) {
        return new ProductDto(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                p.getCategory(), p.getPrice(), p.getCurrency(), p.getStock(), p.isActive(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    public Product toEntity() {
        Product p = new Product();
        p.setSku(sku);
        p.setName(name);
        p.setDescription(description);
        p.setCategory(category);
        p.setPrice(price);
        p.setCurrency(currency == null ? "USD" : currency);
        p.setStock(stock);
        p.setActive(active);
        return p;
    }
}