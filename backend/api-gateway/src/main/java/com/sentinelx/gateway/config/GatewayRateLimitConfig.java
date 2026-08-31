package com.sentinelx.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sentinelx.gateway.filter.JwtAuthGlobalFilter;

import reactor.core.publisher.Mono;

/**
 * Key resolver for the gateway {@code RequestRateLimiter}. Authenticated
 * requests are bucketed per user id; unauthenticated requests per client IP.
 */
@Configuration
public class GatewayRateLimitConfig {

    @Bean
    public KeyResolver keyResolver() {
        return exchange -> {
            String userId = (String) exchange.getAttribute(JwtAuthGlobalFilter.ATTR_USER_ID);
            if (userId != null) {
                return Mono.just("user:" + userId);
            }
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }
}