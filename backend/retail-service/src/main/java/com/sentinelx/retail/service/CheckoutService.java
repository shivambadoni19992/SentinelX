package com.sentinelx.retail.service;

import java.util.ArrayList;
import java.util.List;

import com.sentinelx.retail.entity.CartItem;
import com.sentinelx.retail.entity.Order;
import com.sentinelx.retail.entity.OrderItem;
import com.sentinelx.retail.entity.Product;
import com.sentinelx.retail.repository.CartItemRepository;
import com.sentinelx.retail.repository.OrderItemRepository;
import com.sentinelx.retail.repository.OrderRepository;
import com.sentinelx.retail.repository.ProductRepository;
import com.sentinelx.retail.security.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Checkout application service.
 *
 * <p>Flow: publish {@code CHECKOUT_STARTED} → validate the cart (non-empty,
 * products active, stock available) → reserve stock, persist the order and its
 * line items → clear the cart → publish {@code ORDER_CREATED}. Any validation
 * failure publishes {@code CHECKOUT_FAILED} and surfaces a 4xx error; nothing
 * is persisted.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final CartItemRepository carts;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final CartService cartService;
    private final RetailEventPublisher events;

    public CheckoutService(CartItemRepository carts,
                           ProductRepository products,
                           OrderRepository orders,
                           OrderItemRepository orderItems,
                           CartService cartService,
                           RetailEventPublisher events) {
        this.carts = carts;
        this.products = products;
        this.orders = orders;
        this.orderItems = orderItems;
        this.cartService = cartService;
        this.events = events;
    }

    @Transactional
    public Order checkout(RequestContext caller) {
        var cart = cartService.buildCart(caller.authenticatedSubject());
        int itemCount = cart.items().stream().mapToInt(CartService.CartLine::quantity).sum();

        events.checkoutStarted(caller.authenticatedSubject(), itemCount);

        if (cart.items().isEmpty()) {
            events.checkoutFailed(caller.authenticatedSubject(), "EMPTY_CART");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart is empty");
        }

        List<OrderItem> lines = new ArrayList<>();
        for (CartService.CartLine line : cart.items()) {
            Product p = products.findById(line.productId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Product no longer exists"));
            if (!p.isActive()) {
                events.checkoutFailed(caller.authenticatedSubject(), "PRODUCT_INACTIVE:" + p.getSku());
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Product " + p.getSku() + " is no longer available");
            }
            if (p.getStock() < line.quantity()) {
                events.checkoutFailed(caller.authenticatedSubject(), "INSUFFICIENT_STOCK:" + p.getSku());
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Insufficient stock for " + p.getSku() + " (" + p.getStock() + " available)");
            }
            p.setStock(p.getStock() - line.quantity());
        }

        Order order = new Order();
        order.setUserId(caller.authenticatedSubject());
        order.setStatus("PENDING");
        order.setTotalAmount(cart.total());
        order.setCurrency(cart.currency());
        order.setPlacedAt(java.time.Instant.now());
        order = orders.saveAndFlush(order);

        for (CartService.CartLine line : cart.items()) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(line.productId());
            item.setProductSku(line.sku());
            item.setUnitPrice(line.unitPrice());
            item.setQuantity(line.quantity());
            item.setLineTotal(line.lineTotal());
            lines.add(orderItems.save(item));
        }
        cartService.clear(caller);

        log.info("order created orderId={} userId={} total={} {} items={} correlationId={}",
                order.getId(), order.getUserId(), order.getTotalAmount(), order.getCurrency(),
                lines.size(), MDC.get("correlationId"));
        events.orderCreated(order.getId(), order.getUserId(),
                order.getTotalAmount().toPlainString(), order.getCurrency(), lines.size());
        return order;
    }
}