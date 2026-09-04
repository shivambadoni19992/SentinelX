package com.sentinelx.detection.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sentinelx.detection.engine.DetectionEngine;
import com.sentinelx.detection.kafka.RecentDetections;
import com.sentinelx.detection.rule.DetectionRule;

/**
 * Read API over the detection engine: the active rule set and the most
 * recent detections raised from live Kafka traffic.
 */
@RestController
@RequestMapping("/api/detections")
public class DetectionController {

    private final DetectionEngine engine;
    private final RecentDetections recent;

    public DetectionController(DetectionEngine engine, RecentDetections recent) {
        this.engine = engine;
        this.recent = recent;
    }

    @GetMapping
    public Map<String, Object> detections(@RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> items = recent.latest(Math.min(limit, 500)).stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("detectionId", r.result().ruleId() + "@" + r.at());
                    m.put("ruleId", r.result().ruleId());
                    m.put("severity", r.result().severity().name());
                    m.put("riskContribution", r.result().riskContribution());
                    m.put("reason", r.result().reason());
                    m.put("recommendedAction", r.result().recommendedAction());
                    m.put("sourceTopic", r.sourceTopic());
                    m.put("subject", r.subject());
                    m.put("correlationId", r.correlationId());
                    m.put("raisedAt", r.at().toString());
                    return m;
                })
                .toList();
        return Map.of("count", items.size(), "detections", items);
    }

    @GetMapping("/rules")
    public Map<String, Object> rules() {
        return Map.of("count", engine.rules().size(),
                "rules", engine.rules().stream().map(DetectionRule::id).toList());
    }
}
