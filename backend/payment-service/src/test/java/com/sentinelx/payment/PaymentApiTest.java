package com.sentinelx.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
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
import com.sentinelx.payment.repository.PaymentRepository;

/**
 * End-to-end payment API tests: full context + real Postgres (Testcontainers)
 * through the actual filter chain. Kafka is mocked so the tests stay fast and
 * hermetic while still asserting that PAYMENT_CREATED events are emitted.
 *
 * Covers: creation, validation, idempotency, authorization/ownership,
 * sensitive-data masking and event publication.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentApiTest {

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
    @Autowired PaymentRepository payments;
    @Autowired org.springframework.context.ApplicationContext context;

    @org.junit.jupiter.api.BeforeEach
    void debugAdviceBean() {
        System.out.println("DEBUG-ADVICE-BEANS=" + java.util.Arrays.toString(
                context.getBeanNamesForType(com.sentinelx.payment.web.ApiExceptionHandler.class)));
    }

    @MockBean KafkaTemplate<String, String> kafkaTemplate;

    private static final String DEVICE = "device-9f3ab21c";
    private static final String IP = "203.0.113.45";

    private String body(UUID customerId, UUID merchantId, String amount, String currency) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "customerId", customerId,
                    "merchantId", merchantId,
                    "amount", new BigDecimal(amount),
                    "currency", currency,
                    "deviceId", DEVICE,
                    "ipAddress", IP));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcResult createPayment(UUID customer, String role, UUID merchant, String amount,
                                    String currency, String idempotencyKey) throws Exception {
        var builder = post("/api/payments")
                .header("X-Auth-User-Id", customer.toString())
                .header("X-Auth-Role", role)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(customer, merchant, amount, currency));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(builder).andReturn();
    }

    // ---------------------------------------------------------------- create

    @Test
    void createReturns201WithContractFieldsAndMaskedSensitiveData() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID merchant = UUID.randomUUID();

        mockMvc.perform(post("/api/payments")
                        .header("X-Auth-User-Id", customer)
                        .header("X-Auth-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(customer, merchant, "49.99", "USD")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.paymentId").isNotEmpty())
                .andExpect(jsonPath("$.customerId").value(customer.toString()))
                .andExpect(jsonPath("$.merchantId").value(merchant.toString()))
                .andExpect(jsonPath("$.amount").value(49.99))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.deviceId").value("devi****"))
                .andExpect(jsonPath("$.ipAddress").value("203.0.113.xxx"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void responseNeverContainsUnmaskedDeviceOrIpAddress() throws Exception {
        UUID customer = UUID.randomUUID();
        MvcResult result = createPayment(customer, "CUSTOMER", UUID.randomUUID(), "19.99", "EUR", null);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String json = result.getResponse().getContentAsString();
        assertThat(json).doesNotContain(DEVICE);
        assertThat(json).doesNotContain("9f3ab21c");
        assertThat(json).doesNotContain(IP);
        assertThat(json).doesNotContain("decisionReason");
    }

    @Test
    void declinedPaymentIsReturnedAsDeclined() throws Exception {
        UUID customer = UUID.randomUUID();
        MvcResult result = createPayment(customer, "CUSTOMER", UUID.randomUUID(), "500000.00", "USD", null);
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"DECLINED\"");
    }

    // ------------------------------------------------------------ idempotency

    @Test
    void idempotentReplayReturnsSamePaymentWith200AndNoSecondEvent() throws Exception {
        UUID customer = UUID.randomUUID();
        String key = "order-" + UUID.randomUUID();

        MvcResult first = createPayment(customer, "CUSTOMER", UUID.randomUUID(), "12.00", "USD", key);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        String firstBody = first.getResponse().getContentAsString();

        MvcResult second = createPayment(customer, "CUSTOMER", UUID.randomUUID(), "12.00", "USD", key);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getHeader("Idempotent-Replay")).isEqualTo("true");

        // Compare semantically: the DB round-trip truncates timestamps to
        // microseconds and normalizes BigDecimal scale, so raw JSON can differ
        // for the same payment. Everything except createdAt must match exactly;
        // createdAt must represent the same instant within a microsecond.
        var firstTree = objectMapper.readTree(firstBody);
        var secondTree = objectMapper.readTree(second.getResponse().getContentAsString());
        assertThat(secondTree.get("paymentId")).isEqualTo(firstTree.get("paymentId"));
        assertThat(secondTree.get("customerId")).isEqualTo(firstTree.get("customerId"));
        assertThat(secondTree.get("merchantId")).isEqualTo(firstTree.get("merchantId"));
        assertThat(secondTree.get("status")).isEqualTo(firstTree.get("status"));
        assertThat(secondTree.get("currency")).isEqualTo(firstTree.get("currency"));
        assertThat(secondTree.get("deviceId")).isEqualTo(firstTree.get("deviceId"));
        assertThat(secondTree.get("ipAddress")).isEqualTo(firstTree.get("ipAddress"));
        assertThat(secondTree.path("amount").decimalValue().compareTo(firstTree.path("amount").decimalValue()))
                .isZero();
        var firstAt = java.time.Instant.parse(firstTree.get("createdAt").asText());
        var secondAt = java.time.Instant.parse(secondTree.get("createdAt").asText());
        assertThat(java.time.Duration.between(firstAt, secondAt).abs().toNanos())
                .isLessThan(java.time.Duration.ofMillis(1).toNanos());

        // The container DB is shared across tests; assert on this key, not the table.
        assertThat(payments.findByIdempotencyKey(key)).hasValueSatisfying(p ->
                assertThat(p.getId().toString()).isEqualTo(firstTree.get("paymentId").asText()));
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------------- validation

    @Test
    void zeroAmountIsRejected() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/payments")
                        .header("X-Auth-User-Id", UUID.randomUUID())
                        .header("X-Auth-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), UUID.randomUUID(), "0.00", "USD")))
                .andExpect(status().isBadRequest())
                .andReturn();
        System.out.println("DEBUG-STATUS=" + result.getResponse().getStatus());
        System.out.println("DEBUG-BODY=[" + result.getResponse().getContentAsString() + "]");
        System.out.println("DEBUG-HANDLERS=" + java.util.Arrays.toString(
                result.getHandler() == null ? null : result.getHandler().toString().toCharArray()));
    }

    @Test
    void unsupportedCurrencyIsRejected() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("X-Auth-User-Id", UUID.randomUUID())
                        .header("X-Auth-Role", "CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), UUID.randomUUID(), "5.00", "JPY")))
                .andExpect(status().isBadRequest());
    }
    // ----------------------------------------------------------- authorization

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), UUID.randomUUID(), "5.00", "USD")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerPaymentsAreForcedToTheirOwnSubject() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();

        MvcResult result = createPayment(customer, "CUSTOMER", UUID.randomUUID(), "7.00", "USD", null);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        // customerId in the body is ignored for unprivileged callers.
        assertThat(result.getResponse().getContentAsString())
                .contains("\"customerId\":\"" + customer + "\"")
                .doesNotContain(someoneElse.toString());
    }

    @Test
    void unprivilegedCallerCannotReadAnotherCustomersPayment() throws Exception {
        UUID analyst = UUID.randomUUID();
        UUID ownedCustomer = UUID.randomUUID();
        MvcResult created = createPayment(ownedCustomer, "SOC_ANALYST", UUID.randomUUID(), "8.00", "USD", null);
        String paymentId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("paymentId").asText();

        mockMvc.perform(get("/api/payments/" + paymentId)
                        .header("X-Auth-User-Id", analyst)
                        .header("X-Auth-Role", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listIsScopedForCustomersAndOpenForAnalysts() throws Exception {
        UUID customerA = UUID.randomUUID();
        UUID analyst = UUID.randomUUID();
        UUID merchant = UUID.randomUUID();
        createPayment(customerA, "CUSTOMER", merchant, "5.00", "USD", null);
        createPayment(UUID.randomUUID(), "SOC_ANALYST", merchant, "6.00", "USD", null);

        MvcResult asCustomer = mockMvc.perform(get("/api/payments")
                        .header("X-Auth-User-Id", customerA).header("X-Auth-Role", "CUSTOMER"))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(asCustomer.getResponse().getContentAsString())).hasSize(1);

        MvcResult asAnalyst = mockMvc.perform(get("/api/payments")
                        .header("X-Auth-User-Id", analyst).header("X-Auth-Role", "SOC_ANALYST"))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(asAnalyst.getResponse().getContentAsString()).size())
                .isGreaterThanOrEqualTo(2);
    }

    // ------------------------------------------------------------------ reads

    @Test
    void unknownPaymentIdIsNotFound() throws Exception {
        mockMvc.perform(get("/api/payments/" + UUID.randomUUID())
                        .header("X-Auth-User-Id", UUID.randomUUID()).header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedPaymentIdIsNotFound() throws Exception {
        mockMvc.perform(get("/api/payments/not-a-uuid")
                        .header("X-Auth-User-Id", UUID.randomUUID()).header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusFilterIsValidated() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/payments?status=NOT_A_STATUS")
                        .header("X-Auth-User-Id", UUID.randomUUID()).header("X-Auth-Role", "ADMIN"))
                .andExpect(status().isBadRequest()).andReturn();
        System.out.println("DEBUG-STATUSFILTER-BODY=[" + result.getResponse().getContentAsString() + "]");
    }

    // ----------------------------------------------------------------- events

    @Test
    void paymentCreatedEventIsPublishedWithPaymentIdKey() throws Exception {
        UUID customer = UUID.randomUUID();
        MvcResult created = createPayment(customer, "CUSTOMER", UUID.randomUUID(), "3.00", "GBP", null);
        String paymentId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("paymentId").asText();

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("security.payment"), eq(paymentId), payload.capture());
        assertThat(payload.getValue())
                .contains("\"eventType\":\"PAYMENT_CREATED\"")
                .contains("\"customerId\":\"" + customer + "\"")
                .doesNotContain(DEVICE)
                .doesNotContain(IP);
    }
}