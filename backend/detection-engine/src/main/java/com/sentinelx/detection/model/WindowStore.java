package com.sentinelx.detection.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window state shared by all detection rules. The Kafka
 * consumer records every ingested event here; rules only read through
 * {@link DetectionContext}, keeping rules stateless and composable.
 *
 * <p>Two kinds of state are tracked:
 * <ul>
 *   <li>time-bounded event windows (spikes, velocity, distinct-value scans),
 *       pruned to {@link #RETENTION} on every write;</li>
 *   <li>all-time "seen before" registries (new device / new IP), bounded per
 *       scope to keep memory flat.</li>
 * </ul>
 */
@Component
public class WindowStore {

    public static final Duration RETENTION = Duration.ofHours(1);
    private static final int MAX_SEEN_VALUES = 10_000;

    public record Entry(Instant at, Map<String, Object> data) {
    }

    private final Map<String, Deque<Entry>> windows = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> seen = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** Records one event under a scope (e.g. {@code subject:alice}, {@code ip:1.2.3.4}). */
    public void record(String scope, Instant at, Map<String, Object> data) {
        ReentrantLock lock = lockFor(scope);
        lock.lock();
        try {
            Deque<Entry> deque = windows.computeIfAbsent(scope, k -> new ArrayDeque<>());
            deque.addLast(new Entry(at, data));
            Instant cutoff = at.minus(RETENTION);
            while (!deque.isEmpty() && deque.peekFirst().at().isBefore(cutoff)) {
                deque.removeFirst();
            }
        } finally {
            lock.unlock();
        }
    }

    /** Entries for the scope recorded at or after {@code from}. */
    public List<Entry> since(String scope, Instant from) {
        Deque<Entry> deque = windows.get(scope);
        if (deque == null) {
            return List.of();
        }
        ReentrantLock lock = lockFor(scope);
        lock.lock();
        try {
            List<Entry> out = new ArrayList<>(deque.size());
            for (Entry e : deque) {
                if (!e.at().isBefore(from)) {
                    out.add(e);
                }
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    /** Remembers a value (device id, ip, ...) for the scope, all-time and bounded. */
    public void remember(String scope, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Set<String> values = seen.computeIfAbsent(scope, k -> ConcurrentHashMap.newKeySet());
        synchronized (values) {
            if (values.size() >= MAX_SEEN_VALUES) {
                return;
            }
            values.add(value);
        }
    }

    /** Whether the value was remembered for the scope before this moment. */
    public boolean seenBefore(String scope, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        Set<String> values = seen.get(scope);
        return values != null && values.contains(value);
    }

    /** Test support: drop all state. */
    public void clear() {
        windows.clear();
        seen.clear();
        locks.clear();
    }

    private ReentrantLock lockFor(String scope) {
        return locks.computeIfAbsent(scope, k -> new ReentrantLock());
    }
}
