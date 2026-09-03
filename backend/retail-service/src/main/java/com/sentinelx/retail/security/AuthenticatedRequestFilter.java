package com.sentinelx.retail.security;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Edge-trust authorization for retail endpoints (same model as the
 * payment-service): the gateway validates the JWT and forwards the subject as
 * {@code X-Auth-User-Id} / {@code X-Auth-Role}. Any request under
 * {@code /api/} without that identity is refused, so the service cannot be
 * reached anonymously even when its debug port is exposed. Actuator stays
 * open for probes. The correlation id is promoted into the MDC.
 */
@Component
@Order(10)
public class AuthenticatedRequestFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AuthenticatedRequestFilter.class);

    public static final String HEADER_USER_ID = "X-Auth-User-Id";
    public static final String HEADER_ROLE = "X-Auth-Role";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String ATTR_CONTEXT = AuthenticatedRequestFilter.class.getName() + ".context";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response); // actuator, error, static
            return;
        }

        String correlationId = req.getHeader(HEADER_CORRELATION_ID);
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put("correlationId", correlationId);
        }

        try {
            String subject = req.getHeader(HEADER_USER_ID);
            if (subject == null || subject.isBlank()) {
                log.warn("rejected anonymous retail request path={} correlationId={}", path, correlationId);
                writeError(res, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                        "Missing authenticated subject — call through the API gateway with a bearer token.");
                return;
            }

            RequestContext context;
            try {
                context = RequestContext.of(subject, req.getHeader(HEADER_ROLE));
            } catch (IllegalArgumentException e) {
                log.warn("rejected retail request with malformed subject correlationId={}", correlationId);
                writeError(res, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", e.getMessage());
                return;
            }

            req.setAttribute(ATTR_CONTEXT, context);
            chain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void writeError(HttpServletResponse res, int status, String error, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
        res.getWriter().flush();
    }

    /** Convenience accessor for controllers. */
    public static RequestContext contextOf(HttpServletRequest request) {
        Object value = request.getAttribute(ATTR_CONTEXT);
        if (value instanceof RequestContext ctx) {
            return ctx;
        }
        throw new IllegalStateException("Request reached the controller without an authenticated context");
    }

    /** Extracts a UUID path variable, mapping garbage to 404 rather than 500. */
    public static UUID uuidOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}