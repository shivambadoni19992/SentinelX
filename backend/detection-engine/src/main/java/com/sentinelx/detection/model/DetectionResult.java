package com.sentinelx.detection.model;

/**
 * Outcome of a single {@code DetectionRule} evaluation. A matched detection
 * always carries its {@code ruleId}, {@code severity}, how many risk points it
 * contributes to the aggregate score, a human-readable {@code reason} and the
 * {@code recommendedAction} an analyst should take.
 *
 * @param ruleId            stable identifier, e.g. {@code FAILED_LOGIN_SPIKE}
 * @param severity          detection severity
 * @param riskContribution  points contributed to the aggregate risk score (0-100)
 * @param reason            human-readable explanation of what triggered the rule
 * @param recommendedAction the action an analyst / responder should take
 */
public record DetectionResult(
        String ruleId,
        Severity severity,
        int riskContribution,
        String reason,
        String recommendedAction) {

    public DetectionResult {
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId is required");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity is required for rule " + ruleId);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required for rule " + ruleId);
        }
        if (recommendedAction == null || recommendedAction.isBlank()) {
            throw new IllegalArgumentException("recommendedAction is required for rule " + ruleId);
        }
        if (riskContribution < 0 || riskContribution > 100) {
            throw new IllegalArgumentException("riskContribution must be within 0..100 for rule " + ruleId);
        }
    }
}
