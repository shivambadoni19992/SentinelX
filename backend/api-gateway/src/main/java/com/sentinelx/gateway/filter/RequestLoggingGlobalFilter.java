package com.sentinelx.gateway.filter;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Structured request logging: method, path, final status, duration, and the
 * correlation id. Runs early enough to also capture 401/429 produced by other
 * filters.
 */
@Component
@Order(RequestLoggingGlobalFilter.ORDER)
public class RequestLoggingGlobalFilter implements GlobalFilter, Ordered {

    public static final int ORDER = CorrelationIdGlobalFilter.ORDER + 10;

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.nanoTime();
        String method = exchange.getRequest().getMethod() == null
                ? "-" : exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();

        return chain.filter(exchange).doFinally(signal -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            long ms = Duration.ofNanos(System.nanoTime() - start).toMillis();
            String correlationId = (String) exchange.getAttribute(CorrelationIdGlobalFilter.ATTR);
            String userId = (String) exchange.getAttribute(JwtAuthGlobalFilter.ATTR_USER_ID);
            log.info("request method={} path={} status={} duration_ms={} correlationId={} userId={}",
                    method, path, status == null ? "-" : status.value(), ms,
                    correlationId == null ? "-" : correlationId,
                    userId == null ? "-" : userId);
        });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}