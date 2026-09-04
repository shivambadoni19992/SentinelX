package com.sentinelx.risk.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sentinelx.risk.entity.RiskDecision;
import com.sentinelx.risk.kafka.RiskEventPublisher;
import com.sentinelx.risk.model.RiskLevel;
import com.sentinelx.risk.model.RiskSignal;
import com.sentinelx.risk.repository.RiskDecisionRepository;

/**
 * Scores risk per subject from the signals raised by the detection engine.
 * Signal hits are kept in a sliding window ({@link #WINDOW}); every incoming
 * detection recomputes the subject's score, persists a {@link RiskDecision}
 * and publishes a risk event downstream. Each decision carries the full list
 * of reasons so any score can be explained after the fact.
 */
@Service
public class RiskScoringService {

    public static final Duration WINDOW = Duration.ofMinutes(15);
    public static final String RULE_VERSION = "signals-v1";

    private static final Logger log = LoggerFactory.getLogger(RiskScoringService.class);

    /** One contributing signal hit inside the subject's window. */
    public record SignalHit(RiskSignal signal, String ruleId, String reason,
                            String eventId, Instant at) {
    }

    public record ScoredDecision(int score, RiskLevel level, List<String> reasons,
                                 String action, Map<String, Object> factors) {
    }

    private final RiskDecisionRepository repository;
    private final RiskEventPublisher publisher;

    /** subject key -> hits inside the sliding window. */
    private final Map<String, Deque<SignalHit>> windows = new java.util.concurrent.ConcurrentHashMap<>();

    public RiskScoringService(RiskDecisionRepository repository, RiskEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    /**
     * Records a signal hit for the subject and produces a fresh decision.
     *
     * @return the persisted decision, or null when the subject/signal is absent
     */
    public RiskDecision onSignal(String subject, String sourceTopic, String subjectTypeHint,
                                 RiskSignal signal, String ruleId, String reason,
                                 String eventId, Instant at) {
        if (subject == null || subject.isBlank() || signal == null) {
            return null;
        }
        Instant when = at == null ? Instant.now() : at;
        Deque<SignalHit> hits = windows.computeIfAbsent(subject, k -> new ArrayDeque<>());
        synchronized (hits) {
            hits.addLast(new SignalHit(signal, ruleId, reason, eventId, when));
            prune(hits, when);
            ScoredDecision scored = score(new ArrayList<>(hits));
            return persistAndPublish(subject, sourceTopic, subjectTypeHint, eventId, when, scored);
        }
    }

    /** Test support: drop all in-memory state. */
    public void clear() {
        windows.clear();
    }

    /** Pure scoring over the given hits — unit-testable without state. */
    public static ScoredDecision score(List<SignalHit> hits) {
        Map<RiskSignal, Integer> counts = new LinkedHashMap<>();
        Map<RiskSignal, String> evidence = new LinkedHashMap<>();
        for (SignalHit h : hits) {
            counts.merge(h.signal(), 1, Integer::sum);
            evidence.putIfAbsent(h.signal(), h.reason());
        }
        int score = 0;
        List<String> reasons = new ArrayList<>();
        Map<String, Object> signalPoints = new LinkedHashMap<>();
        List<Map<String, String>> factorHits = new ArrayList<>();
        for (Map.Entry<RiskSignal, Integer> e : counts.entrySet()) {
            RiskSignal signal = e.getKey();
            int points = signal.points(e.getValue());
            score += points;
            signalPoints.put(signal.name(), points);
            reasons.add("%s (+%d): %s%s".formatted(
                    signal.label(), points,
                    evidence.get(signal) == null ? signal + " detected" : evidence.get(signal),
                    e.getValue() > 1 ? " — observed %d times".formatted(e.getValue()) : ""));
        }
        for (SignalHit h : hits) {
            Map<String, String> hit = new LinkedHashMap<>();
            hit.put("signal", h.signal().name());
            hit.put("ruleId", h.ruleId() == null ? "" : h.ruleId());
            hit.put("reason", h.reason() == null ? "" : h.reason());
            hit.put("eventId", h.eventId() == null ? "" : h.eventId());
            hit.put("at", h.at().toString());
            factorHits.add(hit);
        }
        int capped = Math.max(0, Math.min(100, score));
        RiskLevel level = RiskLevel.forScore(capped);
        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("signals", signalPoints);
        factors.put("hits", factorHits);
        factors.put("reasons", reasons);
        factors.put("windowMinutes", WINDOW.toMinutes());
        return new ScoredDecision(capped, level, reasons, level.defaultAction(), factors);
    }

    private void prune(Deque<SignalHit> hits, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!hits.isEmpty() && hits.peekFirst().at().isBefore(cutoff)) {
            hits.removeFirst();
        }
    }

    private RiskDecision persistAndPublish(String subject, String sourceTopic, String subjectTypeHint,
                                           String eventId, Instant when, ScoredDecision scored) {
        RiskDecision d = new RiskDecision();
        // Stable, deterministic UUID for string subjects (usernames, IPs, …).
        d.setSubjectId(UUID.nameUUIDFromBytes(subject.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        d.setSubjectType(subjectTypeHint == null ? subjectTypeFor(sourceTopic) : subjectTypeHint);
        d.setRuleVersion(RULE_VERSION);
        d.setRiskLevel(scored.level().name());
        d.setRiskScore(scored.level().toScore(scored.score()));
        d.setFactors(scored.factors());
        d.setAction(scored.action());
        d.setDecisionAt(when);
        try {
            d = repository.save(d);
        } catch (Exception e) {
            log.warn("failed to persist risk decision for subject {}: {}", subject, e.getMessage());
        }
        publisher.publishRiskEvent(subject, sourceTopic, eventId, scored);
        log.info("risk decision subject={} score={} level={} action={} reasons={}",
                subject, scored.score(), scored.level(), scored.action(), scored.reasons().size());
        return d;
    }

    /** Maps the originating topic onto a subject type for persistence. */
    public static String subjectTypeFor(String sourceTopic) {
        if (sourceTopic == null) {
            return "UNKNOWN";
        }
        return switch (sourceTopic) {
            case "security.auth" -> "USER";
            case "security.payment", "security.retail" -> "CUSTOMER";
            case "security.network" -> "IP";
            case "security.api" -> "CLIENT";
            case "security.audit" -> "ACCOUNT";
            default -> "SUBJECT";
        };
    }
}