package com.sentinelx.retail.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.sentinelx.retail.entity.CartItem;
import com.sentinelx.retail.entity.Product;
import com.sentinelx.retail.repository.CartItemRepository;
import com.sentinelx.retail.repository.ProductRepository;
import com.sentinelx.retail.security.RequestContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cart application service. Carts are always scoped to the authenticated
 * subject — a customer can never read or mutate another customer's cart.
 */
@Service
public class CartService {

    /** Request body for adding an item to the cart. */
    public record AddItemRequest(
            @NotNull(message = "productId is required") UUID productId,
            @Positive(message = "quantity must be at least 1")
            @Max(value = 1000, message = "quantity must be at most 1000") int quantity) {
    }

    /** A cart line as returned by the API (unit price resolved server-side). */
    public record CartLine(UUID productId, String sku, String name, int quantity,
                           BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record Cart(UUID userId, List<CartLine> items, BigDecimal total, String currency) {
    }

    private final CartItemRepository carts;
    private final ProductRepository products;

    public CartService(CartItemRepository carts, ProductRepository products) {
        this.carts = carts;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public Cart view(RequestContext caller) {
        return buildCart(caller.authenticatedSubject());
    }

    /** Adds (or merges) a quantity of a product into the caller's cart. */
    @Transactional
    public Cart addItem(RequestContext caller, @Valid AddItemRequest request) {
        UUID userId = caller.authenticatedSubject();
        Product product = products.findById(request.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        if (!product.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product is not available for purchase");
        }

        CartItem item = carts.findByUserIdAndProductId(userId, product.getId())
                .orElseGet(() -> {
                    CartItem created = new CartItem();
                    created.setUserId(userId);
                    created.setProductId(product.getId());
                    created.setQuantity(0);
                    return created;
                });
        item.setQuantity(item.getQuantity() + request.quantity());
        if (item.getQuantity() > product.getStock()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Requested quantity exceeds available stock (" + product.getStock() + ")");
        }
        carts.save(item);
        return buildCart(userId);
    }

    /** Removes a product from the caller's cart (idempotent). */
    @Transactional
    public Cart removeItem(RequestContext caller, UUID productId) {
        carts.deleteByUserIdAndProductId(caller.authenticatedSubject(), productId);
        return buildCart(caller.authenticatedSubject());
    }

    /** Clears the caller's cart; used after a successful checkout. */
    @Transactional
    public void clear(RequestContext caller) {
        carts.deleteByUserId(caller.authenticatedSubject());
    }

    @Transactional(readOnly = true)
    public Cart buildCart(UUID userId) {
        List<CartLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        String currency = "USD";
        for (CartItem item : carts.findByUserIdOrderByCreatedAtAsc(userId)) {
            Product p = products.findById(item.getProductId()).orElse(null);
            if (p == null) {
                continue; // product vanished; skip rather than break the cart
            }
            currency = p.getCurrency();
            BigDecimal lineTotal = p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            lines.add(new CartLine(p.getId(), p.getSku(), p.getName(), item.getQuantity(), p.getPrice(), lineTotal));
            total = total.add(lineTotal);
        }
        return new Cart(userId, List.copyOf(lines), total, currency);
    }
}