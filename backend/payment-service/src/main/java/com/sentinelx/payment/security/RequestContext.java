package com.sentinelx.payment.security;

import java.util.UUID;

/**
 * Identity resolved for the current request. Populated by
 * {@link AuthenticatedRequestFilter} from the {@code X-Auth-User-Id} /
 * {@code X-Auth-Role} headers the API gateway attaches after JWT validation.
 *
 * @param authenticatedSubject the caller's own subject (used to force ownership)
 * @param role                 role claim from the token (may be blank)
 * @param privileged           true for roles allowed to act on behalf of others
 */
public record RequestContext(UUID authenticatedSubject, String role, boolean privileged) {

    /** Roles that may read/create payments for other customers. */
    public static final java.util.Set<String> PRIVILEGED_ROLES =
            java.util.Set.of("ADMIN", "SOC_ANALYST", "SECURITY_ENGINEER");

    public static RequestContext of(String subjectHeader, String roleHeader) {
        UUID subject;
        try {
            subject = UUID.fromString(subjectHeader);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("X-Auth-User-Id is not a valid UUID");
        }
        String role = roleHeader == null ? "" : roleHeader.trim();
        boolean privileged = PRIVILEGED_ROLES.contains(role.toUpperCase());
        return new RequestContext(subject, role, privileged);
    }
}