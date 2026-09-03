package com.sentinelx.retail.controller;

import java.util.List;
import java.util.UUID;

import com.sentinelx.retail.dto.OrderDto;
import com.sentinelx.retail.entity.Order;
import com.sentinelx.retail.repository.OrderRepository;
import com.sentinelx.retail.security.AuthenticatedRequestFilter;
import com.sentinelx.retail.security.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Order API.
 *
 * <pre>
 *   GET /api/retail/orders          list (scoped to the caller unless privileged)
 *   GET /api/retail/orders/{id}     fetch (ownership enforced)
 * </pre>
 * Orders are created exclusively through {@code POST /api/retail/checkout};
 * the service never trusts a client-supplied total or status.
 */
@RestController
@RequestMapping("/api/retail/orders")
public class OrderController {

    private final OrderRepository repository;

    public OrderController(OrderRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<OrderDto> all(
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "status", required = false) String status,
            HttpServletRequest http) {

        RequestContext caller = AuthenticatedRequestFilter.contextOf(http);
        UUID userFilter = AuthenticatedRequestFilter.uuidOrNull(userId);
        UUID scope = caller.privileged() ? userFilter : caller.authenticatedSubject();

        List<Order> result;
        if (scope != null && StringUtils.hasText(status)) {
            result = repository.findByUserIdAndStatus(scope, status);
        } else if (scope != null) {
            result = repository.findByUserId(scope);
        } else {
            result = repository.findAll();
        }
        return result.stream().map(OrderDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> byId(@PathVariable UUID id, HttpServletRequest http) {
        RequestContext caller = AuthenticatedRequestFilter.contextOf(http);
        return repository.findById(id)
                .map(order -> {
                    if (!caller.privileged() && !order.getUserId().equals(caller.authenticatedSubject())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your order");
                    }
                    return ResponseEntity.ok(OrderDto.from(order));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}