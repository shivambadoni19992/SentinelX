package com.sentinelx.retail.controller;

import com.sentinelx.retail.security.AuthenticatedRequestFilter;
import com.sentinelx.retail.security.RequestContext;
import com.sentinelx.retail.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cart API — always scoped to the authenticated subject.
 *
 * <pre>
 *   GET    /api/retail/cart                  view the caller's cart
 *   POST   /api/retail/cart/items            add / merge a line
 *   DELETE /api/retail/cart/items/{productId}  remove a line (idempotent)
 * </pre>
 */
@RestController
@RequestMapping("/api/retail/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartService.Cart view(HttpServletRequest http) {
        RequestContext caller = AuthenticatedRequestFilter.contextOf(http);
        return cartService.view(caller);
    }

    @PostMapping("/items")
    public CartService.Cart add(@Valid @RequestBody CartService.AddItemRequest request, HttpServletRequest http) {
        RequestContext caller = AuthenticatedRequestFilter.contextOf(http);
        return cartService.addItem(caller, request);
    }

    @DeleteMapping("/items/{productId}")
    public CartService.Cart remove(@PathVariable UUID productId, HttpServletRequest http) {
        RequestContext caller = AuthenticatedRequestFilter.contextOf(http);
        return cartService.removeItem(caller, productId);
    }
}