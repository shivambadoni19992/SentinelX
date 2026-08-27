package com.sentinelx.retail;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sentinelx.retail.entity.Order;
import com.sentinelx.retail.entity.OrderItem;
import com.sentinelx.retail.entity.Product;
import com.sentinelx.retail.repository.OrderItemRepository;
import com.sentinelx.retail.repository.OrderRepository;
import com.sentinelx.retail.repository.ProductRepository;

@SpringBootTest
@Testcontainers
class RetailPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    ProductRepository products;

    @Autowired
    OrderRepository orders;

    @Autowired
    OrderItemRepository items;

    @Test
    void productOrderItemRoundTrip() {
        Product p = new Product();
        p.setSku("SKU-001");
        p.setName("Widget");
        p.setCategory("gadgets");
        p.setPrice(new BigDecimal("9.9900"));
        p.setStock(5);
        p = products.saveAndFlush(p);
        assertThat(p.getId()).isNotNull();
        assertThat(products.findByCategory("gadgets")).hasSize(1);

        Order o = new Order();
        o.setUserId(UUID.randomUUID());
        o.setTotalAmount(new BigDecimal("19.9800"));
        o = orders.saveAndFlush(o);
        assertThat(o.getId()).isNotNull();

        OrderItem i = new OrderItem();
        i.setOrderId(o.getId());
        i.setProductId(p.getId());
        i.setProductSku(p.getSku());
        i.setUnitPrice(p.getPrice());
        i.setQuantity(2);
        i.setLineTotal(new BigDecimal("19.9800"));
        i = items.saveAndFlush(i);
        assertThat(i.getId()).isNotNull();
        assertThat(items.findByOrderId(o.getId())).hasSize(1);
    }
}