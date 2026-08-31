package com.sentinelx.auth.domain;

/**
 * Lifecycle state of a SentinelX account (stored in
 * {@code whoami.users.account_status}).
 *
 * <ul>
 *   <li>ACTIVE   - fully permitted.</li>
 *   <li>MONITORED - allowed to authenticate, but the account is flagged for
 *       elevated review (detections may treat it as higher risk).</li>
 *   <li>BLOCKED   - authentication is refused entirely.</li>
 * </ul>
 */
public enum AccountStatus {
    ACTIVE,
    MONITORED,
    BLOCKED
}