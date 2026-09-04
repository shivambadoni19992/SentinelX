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
 * enumeration / scraping).
 */
@Component
public class BotActivityRule implements DetectionRule {

    public static final String RULE_ID = "BOT_ACTIVITY";
    static final Duration WINDOW = Duration.ofMinutes(1);
    static final int DISTINCT_PATH_THRESHOLD = 15;
    static final int VOLUME_THRESHOLD = 50;
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
        String reason = botAgent
                ? "automated user-agent '" + userAgent + "' from " + client(ctx)
                : distinctPaths + " distinct endpoints hit " + volume + " times in "
                        + WINDOW.toMinutes() + " minute(s) by " + client(ctx);
        return new DetectionResult(RULE_ID, Severity.MEDIUM, 20, reason,
                "Serve a CAPTCHA challenge and rate-limit the client");
    }

    private static String client(DetectionContext ctx) {
        return ctx.sourceIp() != null ? ctx.sourceIp() : ctx.subjectKey();
    }
}
