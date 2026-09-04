package com.sentinelx.detection.engine;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Composable detection engine: collects every {@link DetectionRule} bean in
 * the context, evaluates the applicable ones against each event, and folds
 * the individual {@code riskContribution} values into one aggregate risk
 * score (capped at 100). A failing rule is logged and skipped so one broken
 * rule can never blind the whole engine.
 */
@Component
public class DetectionEngine {

    private static final Logger log = LoggerFactory.getLogger(DetectionEngine.class);

    private final List<DetectionRule> rules;

    public DetectionEngine(List<DetectionRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** All registered rules, in registration order. */
    public List<DetectionRule> rules() {
        return rules;
    }

    public Evaluation evaluate(DetectionContext context) {
        List<DetectionResult> matches = rules.stream()
                .filter(rule -> rule.appliesTo().isEmpty() || rule.appliesTo().contains(context.topic()))
                .map(rule -> {
                    try {
                        return rule.evaluate(context);
                    } catch (Exception e) {
                        log.warn("rule {} failed on topic={}: {}", rule.id(), context.topic(), e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        int aggregate = Math.min(100, matches.stream().mapToInt(DetectionResult::riskContribution).sum());
        return new Evaluation(matches, aggregate);
    }

    /**
     * @param matches       detections raised for the event
     * @param aggregateRisk sum of contributions, capped at 100
     */
    public record Evaluation(List<DetectionResult> matches, int aggregateRisk) {
    }
}
