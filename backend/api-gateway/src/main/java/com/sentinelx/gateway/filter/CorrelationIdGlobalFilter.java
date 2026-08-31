package com.sentinelx.gateway.filter;

import java.util.UUID;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Ensures every request has a {@code X-Correlation-Id}. If the caller supplies
 * one it is propagated; otherwise a fresh {@link UUID} is generated. The id is
 * forwarded upstream (so backend logs can tie into the same trace) and echoed
 * back on the response.
 */
@Component
@Order(CorrelationIdGlobalFilter.ORDER)
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTR = CorrelationIdGlobalFilter.class.getName() + ".correlationId";
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER, correlationId)
                .build();
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().set(HEADER, correlationId);
        exchange.getAttributes().put(ATTR, correlationId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}