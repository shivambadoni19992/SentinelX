package com.sentinelx.retail.controller;

import com.sentinelx.retail.dto.OrderDto;
import com.sentinelx.retail.security.AuthenticatedRequestFilter;
import com.sentinelx.retail.security.RequestContext;
import com.sentinelx.retail.service.CheckoutService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout API.
 *
 * <pre>
 *   POST /api/retail/checkout   convert the caller's cart into an order
 * </pre>
 * Emits CHECKOUT_STARTED / CHECKOUT_FAILED / ORDER_CREATED events; the total
 * is computed server-side from catalog prices, never from the client.
 */
@RestController
@RequestMapping("/api/retail/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public ResponseEntity<OrderDto> checkout(HttpServletRequest http) {
        RequestContext caller = AuthenticatedRequestFilter.contextOf(http);
        OrderDto order = OrderDto.from(checkoutService.checkout(caller));
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
}