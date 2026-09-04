package com.sentinelx.simulation.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sentinelx.simulation.dto.SimulationRunDto;
import com.sentinelx.simulation.service.SimulationService;

/**
 * Simulation API. POST starts a run of the given type with a validated
 * SimulationConfig; GET exposes run progress (counters are updated by the
 * engine and the downstream pipeline tracker). No endpoint creates alerts.
 */
@RestController
@RequestMapping("/api/simulations")
public class SimulationRunController {

    private static final Logger log = LoggerFactory.getLogger(SimulationRunController.class);

    private final SimulationService service;

    public SimulationRunController(SimulationService service) {
        this.service = service;
    }

    /** Starts a new simulation. Body: {type, configuration?, name?, runBy?}. */
    @PostMapping
    public ResponseEntity<SimulationRunDto> create(@RequestBody Map<String, Object> body) {
        SimulationRunDto created = service.create(
                (String) body.get("type"),
                body.get("configuration") != null ? body.get("configuration") : body.get("config"),
                (String) body.get("name"),
                (String) body.get("runBy"));
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping
    public List<SimulationRunDto> all() {
        return service.all();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SimulationRunDto> byId(@PathVariable UUID id) {
        SimulationRunDto run = service.byId(id);
        return run == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(run);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable UUID id) {
        try {
            SimulationRunDto cancelled = service.cancel(id);
            return cancelled == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(cancelled);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    /** Validation failures (over-limit configs, unknown types) → 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** Capacity conflicts → 409. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }
}
