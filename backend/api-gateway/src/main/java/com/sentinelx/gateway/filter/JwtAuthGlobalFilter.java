package com.sentinelx.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.sentinelx.gateway.security.JwtValidator;

import io.jsonwebtoken.Claims;
import reactor.core.publisher.Mono;

/**
 * Edge JWT enforcement. Public endpoints (login, actuator, system health)
 * pass through; everything else requires a valid {@code Bearer} token.
 *
 * <p>On success the authenticated user id/role are passed upstream as
 * {@code X-Auth-User-Id} / {@code X-Auth-Role} and stored in exchange
 * attributes (used by the rate limiter key resolver).
 */
@Component
@Order(JwtAuthGlobalFilter.ORDER)
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    public static final int ORDER = -100;

    public static final String ATTR_USER_ID = JwtAuthGlobalFilter.class.getName() + ".userId";
    public static final String ATTR_ROLE = JwtAuthGlobalFilter.class.getName() + ".role";
    public static final String HEADER_USER_ID = "X-Auth-User-Id";
    public static final String HEADER_ROLE = "X-Auth-Role";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator jwtValidator;
    private final ErrorResponseWriter errorWriter;

    public JwtAuthGlobalFilter(JwtValidator jwtValidator, ErrorResponseWriter errorWriter) {
        this.jwtValidator = jwtValidator;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = (auth != null && auth.startsWith(BEARER_PREFIX))
                ? auth.substring(BEARER_PREFIX.length()).trim()
                : null;

        Claims claims = token == null ? null : jwtValidator.claimsOrNull(token);
        if (claims == null) {
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED,
                    "Unauthorized", "Missing or invalid bearer token");
        }

        String subject = claims.getSubject();
        String role = claims.get("role", String.class);

        exchange.getAttributes().put(ATTR_USER_ID, subject);
        exchange.getAttributes().put(ATTR_ROLE, role == null ? "" : role);

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER_USER_ID, subject)
                .header(HEADER_ROLE, role == null ? "" : role)
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isPublic(String path) {
        return path.equals("/api/auth/login")
                || path.startsWith("/actuator")
                || path.startsWith("/api/system")
                || path.equals("/error")
                || path.equals("/favicon.ico");
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}