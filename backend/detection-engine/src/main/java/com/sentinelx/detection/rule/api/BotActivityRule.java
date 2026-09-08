package com.sentinelx.detection.rule.api;

import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.model.DetectionContext;
import com.sentinelx.detection.model.DetectionResult;
import com.sentinelx.detection.model.Severity;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Fires on automated client behaviour: an explicit bot user-agent, or a
 * client sweeping many distinct endpoints at high request volume (endpoint
 * enumeration / scraping). Severity escalates when a confirmed bot floods a
 * single endpoint:
 *
 * <ul>
 *   <li>bot user-agent detected — MEDIUM (riskContribution 20)</li>
 *   <li>bot user-agent + 100+ requests in the window — HIGH (riskContribution 35)</li>
 *   <li>bot user-agent + 500+ requests in the window — CRITICAL (riskContribution 55)</li>
 * </ul>
 */
@Component
public class BotActivityRule implements DetectionRule {

    public static final String RULE_ID = "BOT_ACTIVITY";
    static final Duration WINDOW = Duration.ofMinutes(1);
    static final int DISTINCT_PATH_THRESHOLD = 15;
    static final int VOLUME_THRESHOLD = 50;
    static final int HIGH_VOLUME_THRESHOLD = 100;
    static final int CRITICAL_VOLUME_THRESHOLD = 500;
    static final Pattern BOT_USER_AGENT = Pattern.compile(
            "(?i).*(bot|crawler|spider|scraper|curl|wget|python-requests|headless).*");

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public Set<String> appliesTo() {
        return Set.of("security.api");
    }

    @Override
    public DetectionResult evaluate(DetectionContext ctx) {
        String userAgent = ctx.text("userAgent", "user_agent");
        boolean botAgent = userAgent != null && BOT_USER_AGENT.matcher(userAgent).matches();
        long volume = ctx.countInWindow(ctx.ipScope(), WINDOW,
                e -> "security.api".equals(e.get("topic")));
        int distinctPaths = ctx.distinctInWindow(ctx.ipScope(), WINDOW, "path",
                e -> "security.api".equals(e.get("topic"))).size();
        if (!botAgent && !(distinctPaths >= DISTINCT_PATH_THRESHOLD && volume >= VOLUME_THRESHOLD)) {
            return null;
        }
        if (botAgent && volume >= CRITICAL_VOLUME_THRESHOLD) {
            return new DetectionResult(RULE_ID, Severity.CRITICAL, 55,
                    "bot user-agent '" + userAgent + "' from " + client(ctx) + " at " + volume
                            + " requests in " + WINDOW.toSeconds() + "s — bot-flood in progress",
                    "RATE_LIMIT — block bot IP " + client(ctx) + " at the gateway");
        }
        if (botAgent && volume >= HIGH_VOLUME_THRESHOLD) {
            return new DetectionResult(RULE_ID, Severity.HIGH, 35,
                    "bot user-agent '" + userAgent + "' from " + client(ctx) + " at " + volume
                            + " requests in " + WINDOW.toSeconds() + "s",
                    "RATE_LIMIT — throttle bot IP " + client(ctx) + " and serve a CAPTCHA");
        }
        String reason = botAgent
                ? "automated user-agent '" + userAgent + "' from " + client(ctx)
                : distinctPaths + " distinct endpoints hit " + volume + " times in "
                        + WINDOW.toMinutes() + " minute(s) by " + client(ctx);
        String action = botAgent
                ? "RATE_LIMIT — apply Redis rate-limit for " + client(ctx)
                : "Serve a CAPTCHA challenge and rate-limit the client";
        return new DetectionResult(RULE_ID, Severity.MEDIUM, 20, reason, action);
    }

    private static String client(DetectionContext ctx) {
        return ctx.sourceIp() != null ? ctx.sourceIp() : ctx.subjectKey();
    }
}
