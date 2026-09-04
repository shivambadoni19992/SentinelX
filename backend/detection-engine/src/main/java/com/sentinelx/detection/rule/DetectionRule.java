package com.sentinelx.detection.rule;

import java.util.Set;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;

/**
 * One composable detection rule. Implementations are stateless Spring beans;
 * all shared state is reached through the {@link DetectionContext}. A rule is
 * composed into the engine simply by declaring it as a {@code @Component} —
 * {@code DetectionEngine} collects every {@code DetectionRule} bean and runs
 * the ones whose {@link #appliesTo()} topics match the incoming event.
 *
 * <p>{@link #evaluate} returns a {@link DetectionResult} when the rule fires
 * and {@code null} when it does not. Rules must never throw: the engine
 * isolates individual rule failures.
 */
public interface DetectionRule {

    /** Stable rule identifier, e.g. {@code FAILED_LOGIN_SPIKE}. */
    String id();

    /** Topics this rule evaluates; empty means every topic. */
    default Set<String> appliesTo() {
        return Set.of();
    }

    /** Evaluates the event; {@code null} means "no detection". */
    DetectionResult evaluate(DetectionContext context);
}
