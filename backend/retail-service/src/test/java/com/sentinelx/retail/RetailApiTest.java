package com.sentinelx.retail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelx.retail.repository.ProductRepository;

/**
 * End-to-end retail commerce API tests: full context + real Postgres
 * (Testcontainers) through the actual filter chain. Kafka is mocked while
 * still asserting that CHECKOUT_STARTED / CHECKOUT_FAILED / ORDER_CREATED
 * events are emitted.
 *
 * Covers: catalog browsing, privileged product creation, cart validation,
 * checkout success/failure, authorization scoping and event publication.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RetailApiTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProductRepository products;

    @MockBean KafkaTemplate<String, String> kafkaTemplate;

    private MvcResult listProducts(String role) throws Exception {
        return mockMvc.perform(get("/api/retail/products")
                        .header("X-Auth-User-Id", UUID.randomUUID()).header("X-Auth-Role", role))
                .andExpect(status().isOk()).andReturn();
    }

    private String firstProductId() throws Exception {
        return objectMapper.readTree(listProducts("ADMIN").getResponse().getContentAsString())
                .get(0).path("id").asText();
    }

    private MvcResult addToCart(UUID user, String role, String productId, int qty) throws Exception {
        return mockMvc.perform(post("/api/retail/cart/items")
                        .header("X-Auth-User-Id", user).header("X-Auth-Role", role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("productId", productId, "quantity", qty))))
                .andReturn();
    }

    // ------------------------------------------------------------------ auth

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/retail/products")).andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------- products

    @Test
    void catalogIsBrowsableByAnyAuthenticatedCaller() throws Exception {
        var body = objectMapper.readTree(listProducts("CUSTOMER").getResponse().getContentAsString());
        assertThat(body.size()).isGreaterThanOrEqualTo(12); // synthetic seeder ran
        assertThat(body.get(0).path("sku").asText()).startsWith("SNX-");
    }

    @Test
    void onlyPrivilegedRolesCanCreateProducts() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "sku", "SNX-TEST-1", "name", "Test Widget", "price", "9.99", "currency", "USD", "stock", 5));

        mockMvc.perform(post("/api/retail/products")
                        .header("X-Auth-User-Id", UUID.randomUUID()).header("X-Auth-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/retail/products")
                        .header("X-Auth-User-Id", UUID.randomUUID()).header("X-Auth-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SNX-TEST-1"));
    }

    // ------------------------------------------------------------------ cart

    @Test
    void cartValidationRejectsBadQuantities() throws Exception {
        UUID user = UUID.randomUUID();
        String productId = firstProductId();

        mockMvc.perform(post("/api/retail/cart/items")
                        .header("X-Auth-User-Id", user).header("X-Auth-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/retail/cart/items")
                        .header("X-Auth-User-Id", user).header("X-Auth-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"not-a-uuid\",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cartIsScopedPerUserAndMergesQuantities() throws Exception {
        UUID userA = UUID.randomUUID();
        String productId = firstProductId();

        addToCart(userA, "CUSTOMER", productId, 1);
        MvcResult second = addToCart(userA, "CUSTOMER", productId, 2);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        var cart = objectMapper.readTree(second.getResponse().getContentAsString());
        assertThat(cart.get("items").get(0).path("quantity").asInt()).isEqualTo(3);

        MvcResult other = mockMvc.perform(get("/api/retail/cart")
                        .header("X-Auth-User-Id", UUID.randomUUID()).header("X-Auth-Role", "CUSTOMER"))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(other.getResponse().getContentAsString()).get("items").size()).isZero();
    }

    // -------------------------------------------------------------- checkout

    @Test
    void checkoutCreatesOrderDecrementsStockAndPublishesEvents() throws Exception {
        UUID user = UUID.randomUUID();
        String productId = firstProductId();
        int stockBefore = products.findById(UUID.fromString(productId)).orElseThrow().getStock();

        addToCart(user, "CUSTOMER", productId, 2);

        MvcResult result = mockMvc.perform(post("/api/retail/checkout")
                        .header("X-Auth-User-Id", user).header("X-Auth-Role", "CUSTOMER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        var order = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(order.path("totalAmount").decimalValue()).isPositive();

        assertThat(products.findById(UUID.fromString(productId)).orElseThrow().getStock())
                .isEqualTo(stockBefore - 2);

        MvcResult cart = mockMvc.perform(get("/api/retail/cart")
                        .header("X-Auth-User-Id", user).header("X-Auth-Role", "CUSTOMER"))
                .andReturn();
        assertThat(objectMapper.readTree(cart.getResponse().getContentAsString()).get("items").size()).isZero();

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(2)).send(eq("sentinelx.retail.events"), anyString(), payload.capture());
        assertThat(payload.getAllValues().get(0)).contains("\"eventType\":\"CHECKOUT_STARTED\"");
        assertThat(payload.getAllValues().get(1))
                .contains("\"eventType\":\"ORDER_CREATED\"")
                .contains("\"orderId\":\"" + order.path("id").asText() + "\"");
    }

    @Test
    void checkoutFailsWithEventOnInsufficientStock() throws Exception {
        // Cart adds are already stock-validated, so create a race: two users buy
        // the same product; the second checkout must fail with CHECKOUT_FAILED.
        UUID buyerA = UUID.randomUUID();
        UUID buyerB = UUID.randomUUID();
        String productId = firstProductId();
        int stock = products.findById(UUID.fromString(productId)).orElseThrow().getStock();

        addToCart(buyerA, "CUSTOMER", productId, stock);
        addToCart(buyerB, "CUSTOMER", productId, 1);

        mockMvc.perform(post("/api/retail/checkout")
                        .header("X-Auth-User-Id", buyerA).header("X-Auth-Role", "CUSTOMER"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/retail/checkout")
                        .header("X-Auth-User-Id", buyerB).header("X-Auth-Role", "CUSTOMER"))
                .andExpect(status().isConflict());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, org.mockito.Mockito.atLeastOnce()).send(anyString(), anyString(), payload.capture());
        assertThat(payload.getAllValues().stream().anyMatch(p -> p.contains("\"eventType\":\"CHECKOUT_FAILED\"")))
                .isTrue();
    }

    @Test
    void checkoutFailsOnEmptyCart() throws Exception {
        mockMvc.perform(post("/api/retail/checkout")
                        .header("X-Auth-User-Id", UUID.randomUUID()).header("X-Auth-Role", "CUSTOMER"))
                .andExpect(status().isConflict());
    }

    // ---------------------------------------------------------------- orders

    @Test
    void ordersAreScopedToTheirOwner() throws Exception {
        UUID owner = UUID.randomUUID();
        String productId = firstProductId();
        addToCart(owner, "CUSTOMER", productId, 1);
        mockMvc.perform(post("/api/retail/checkout")
                        .header("X-Auth-User-Id", owner).header("X-Auth-Role", "CUSTOMER"))
                .andExpect(status().isCreated());

        MvcResult own = mockMvc.perform(get("/api/retail/orders")
                        .header("X-Auth-User-Id", owner).header("X-Auth-Role", "CUSTOMER"))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(own.getResponse().getContentAsString())).hasSize(1);

        MvcResult stranger = mockMvc.perform(get("/api/retail/orders")
                        .header("X-Auth-User-Id", UUID.randomUUID()).header("X-Auth-Role", "CUSTOMER"))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(stranger.getResponse().getContentAsString())).isEmpty();

        mockMvc.perform(get("/api/retail/orders")
                        .header("X-Auth-User-Id", UUID.randomUUID()).header("X-Auth-Role", "SOC_ANALYST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNotEmpty());

        String orderId = objectMapper.readTree(own.getResponse().getContentAsString()).get(0).path("id").asText();
        mockMvc.perform(get("/api/retail/orders/" + orderId)
                        .header("X-Auth-User-Id", UUID.randomUUID()).header("X-Auth-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }
}
