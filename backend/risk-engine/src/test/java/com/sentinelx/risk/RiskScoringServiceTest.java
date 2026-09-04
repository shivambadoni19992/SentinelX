package com.sentinelx.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sentinelx.risk.engine.RiskScoringService;
import com.sentinelx.risk.engine.RiskScoringService.ScoredDecision;
import com.sentinelx.risk.engine.RiskScoringService.SignalHit;
import com.sentinelx.risk.entity.RiskDecision;
import com.sentinelx.risk.kafka.RiskEventPublisher;
import com.sentinelx.risk.model.RiskLevel;
import com.sentinelx.risk.model.RiskSignal;
import com.sentinelx.risk.repository.RiskDecisionRepository;

/**
 * Unit coverage for the risk scoring service: signal weights, stacking cap,
 * level bands, explainable reasons, persistence and event publication.
 */
class RiskScoringServiceTest {

    private RiskDecisionRepository repository;
    private RiskEventPublisher publisher;
    private RiskScoringService service;

    @BeforeEach
    void setUp() {
        repository = mock(RiskDecisionRepository.class);
        publisher = mock(RiskEventPublisher.class);
        when(repository.save(any(RiskDecision.class)))
                .thenAnswer(inv -> inv.getArgument(0, RiskDecision.class));
        service = new RiskScoringService(repository, publisher);
    }

    private static SignalHit hit(RiskSignal signal, Instant at) {
        return new SignalHit(signal, signal.ruleIds().iterator().next(),
                signal.label() + " detected", "evt-" + signal, at);
    }

    @Test
    @DisplayName("no signals scores LOW / ALLOW")
    void emptyIsLow() {
        ScoredDecision d = RiskScoringService.score(List.of());
        assertThat(d.score()).isZero();
        assertThat(d.level()).isEqualTo(RiskLevel.LOW);
        assertThat(d.action()).isEqualTo("ALLOW");
        assertThat(d.reasons()).isEmpty();
    }

    @Test
    @DisplayName("subject type derived from source topic when no hint given")
    void subjectTypeFromTopic() {
        assertThat(RiskScoringService.subjectTypeFor("security.auth")).isEqualTo("USER");
        assertThat(RiskScoringService.subjectTypeFor("security.payment")).isEqualTo("CUSTOMER");
        assertThat(RiskScoringService.subjectTypeFor("security.network")).isEqualTo("IP");
        assertThat(RiskScoringService.subjectTypeFor("security.api")).isEqualTo("CLIENT");
        assertThat(RiskScoringService.subjectTypeFor(null)).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("blank subjects and unknown signals are ignored")
    void ignoresInvalidInput() {
        assertThat(service.onSignal("", "security.auth", null,
                RiskSignal.FAILED_LOGINS, "FAILED_LOGIN_SPIKE", "r", "e", Instant.now())).isNull();
        assertThat(service.onSignal(null, "security.auth", null,
                RiskSignal.FAILED_LOGINS, "FAILED_LOGIN_SPIKE", "r", "e", Instant.now())).isNull();
        assertThat(service.onSignal("bob", "security.auth", null,
                null, "X", "r", "e", Instant.now())).isNull();
    }

    @Test
    @DisplayName("weights sum and band boundaries hold (55 MEDIUM, 75 HIGH, 95 CRITICAL)")
    void weightsAndBands() {
        // failedLogins 20 + newIp 10 + transactionAmount 25 = 55 → MEDIUM
        ScoredDecision medium = RiskScoringService.score(List.of(
                hit(RiskSignal.FAILED_LOGINS, Instant.now()),
                hit(RiskSignal.NEW_IP, Instant.now()),
                hit(RiskSignal.TRANSACTION_AMOUNT, Instant.now())));
        assertThat(medium.score()).isEqualTo(55);
        assertThat(medium.level()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(medium.action()).isEqualTo("REVIEW");

        // + failedPayments 20 = 75 → HIGH
        ScoredDecision high = RiskScoringService.score(List.of(
                hit(RiskSignal.FAILED_LOGINS, Instant.now()),
                hit(RiskSignal.NEW_IP, Instant.now()),
                hit(RiskSignal.TRANSACTION_AMOUNT, Instant.now()),
                hit(RiskSignal.FAILED_PAYMENTS, Instant.now())));
        assertThat(high.score()).isEqualTo(75);
        assertThat(high.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(high.action()).isEqualTo("CHALLENGE");

        // + botActivity 20 = 95 → CRITICAL
        ScoredDecision critical = RiskScoringService.score(List.of(
                hit(RiskSignal.FAILED_LOGINS, Instant.now()),
                hit(RiskSignal.NEW_IP, Instant.now()),
                hit(RiskSignal.TRANSACTION_AMOUNT, Instant.now()),
                hit(RiskSignal.FAILED_PAYMENTS, Instant.now()),
                hit(RiskSignal.BOT_ACTIVITY, Instant.now())));
        assertThat(critical.score()).isEqualTo(95);
        assertThat(critical.level()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(critical.action()).isEqualTo("BLOCK");
    }

    @Test
    @DisplayName("repeated signals stack up to 2x weight, capped at 100")
    void stackingCap() {
        Instant now = Instant.now();
        ScoredDecision d = RiskScoringService.score(List.of(
                hit(RiskSignal.DATA_ACCESS_ANOMALY, now),
                hit(RiskSignal.DATA_ACCESS_ANOMALY, now),
                hit(RiskSignal.DATA_ACCESS_ANOMALY, now))); // capped at 2x30 = 60
        assertThat(d.score()).isEqualTo(60);
        assertThat(d.reasons().get(0)).contains("observed 3 times");

        ScoredDecision capped = RiskScoringService.score(List.of(
                hit(RiskSignal.DATA_ACCESS_ANOMALY, now),
                hit(RiskSignal.DATA_ACCESS_ANOMALY, now),
                hit(RiskSignal.NETWORK_ANOMALY, now),
                hit(RiskSignal.NETWORK_ANOMALY, now),
                hit(RiskSignal.TRANSACTION_AMOUNT, now),
                hit(RiskSignal.TRANSACTION_AMOUNT, now))); // 60+50+50=160 → 100
        assertThat(capped.score()).isEqualTo(100);
        assertThat(capped.level()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    @DisplayName("every reason is human-readable and names the signal")
    void reasonsExplainScore() {
        ScoredDecision d = RiskScoringService.score(List.of(
                hit(RiskSignal.FAILED_LOGINS, Instant.now()),
                hit(RiskSignal.NEW_DEVICE, Instant.now())));
        assertThat(d.reasons()).hasSize(2);
        assertThat(d.reasons().get(0)).startsWith("failed logins (+20)");
        assertThat(d.reasons().get(1)).startsWith("new device (+15)");
    }

    @Test
    @DisplayName("factors carry signals, hits and reasons for the UI")
    void factorsAreExplainable() {
        ScoredDecision d = RiskScoringService.score(List.of(hit(RiskSignal.API_RATE, Instant.now())));
        Map<?, ?> signals = (Map<?, ?>) d.factors().get("signals");
        assertThat(signals.get("API_RATE")).isEqualTo(15);
        assertThat((List<?>) d.factors().get("reasons")).isNotEmpty();
        assertThat((List<?>) d.factors().get("hits")).hasSize(1);
    }

    @Test
    @DisplayName("onSignal persists a decision and publishes a risk event")
    void onSignalPersistsAndPublishes() {
        Instant now = Instant.now();
        RiskDecision d = service.onSignal("alice", "security.auth", "USER",
                RiskSignal.FAILED_LOGINS, "FAILED_LOGIN_SPIKE", "5 failed logins", "evt-1", now);

        assertThat(d).isNotNull();
        assertThat(d.getSubjectId()).isNotNull();
        assertThat(d.getSubjectType()).isEqualTo("USER");
        assertThat(d.getRiskLevel()).isEqualTo("LOW");
        assertThat(d.getAction()).isEqualTo("ALLOW");
        assertThat(d.getFactors()).containsKey("reasons");
        verify(repository).save(any(RiskDecision.class));
        verify(publisher, times(1)).publishRiskEvent(any(), any(), any(), any());
    }
}