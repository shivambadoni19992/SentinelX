package com.sentinelx.detection.kafka;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sentinelx.detection.engine.DetectionEngine;
import com.sentinelx.detection.model.DetectionResult;

/**
 * Bounded in-memory ring buffer of the most recent detections, served by the
 * {@code /api/detections} endpoint so the dashboard can display live output
 * without querying Kafka directly.
 */
@Component
public class RecentDetections {

    private static final int CAPACITY = 500;

    public record Raised(Instant at, String sourceTopic, String subject, String correlationId,
                         DetectionResult result) {
    }

    private final Deque<Raised> recent = new ArrayDeque<>();

    public synchronized void add(Raised detection) {
        recent.addFirst(detection);
        while (recent.size() > CAPACITY) {
            recent.removeLast();
        }
    }

    public synchronized List<Raised> latest(int limit) {
        List<Raised> out = new ArrayList<>();
        for (Raised r : recent) {
            if (out.size() >= limit) {
                break;
            }
            out.add(r);
        }
        return out;
    }

    public synchronized void clear() {
        recent.clear();
    }
}
