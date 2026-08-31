package com.sentinelx.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Adds baseline security response headers to every response that passes
 * through the gateway.
 */
@Component
@Order(SecurityHeadersGlobalFilter.ORDER)
public class SecurityHeadersGlobalFilter implements GlobalFilter, Ordered {

    public static final int ORDER = CorrelationIdGlobalFilter.ORDER + 100;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();

        response.getHeaders().add("X-Content-Type-Options", "nosniff");
        response.getHeaders().add("X-Frame-Options", "DENY");
        response.getHeaders().add("X-XSS-Protection", "1; mode=block");
        response.getHeaders().add("Referrer-Policy", "no-referrer");
        response.getHeaders().add("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        response.getHeaders().add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}