package com.sentinelx.alert.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sentinelx.alert.domain.AlertAction;
import com.sentinelx.alert.domain.AlertStatus;
import com.sentinelx.alert.dto.SecurityAlertDto;
import com.sentinelx.alert.entity.SecurityAlert;
import com.sentinelx.alert.repository.SecurityAlertRepository;
import com.sentinelx.alert.service.AlertWorkflowService;

/**
 * Alert API: list/create alerts, drive the status lifecycle
 * (OPEN → INVESTIGATING → RESOLVED / FALSE_POSITIVE) and apply response
 * actions that change real application state.
 */
@RestController
@RequestMapping("/api/alerts")
public class SecurityAlertController {

    private final SecurityAlertRepository repository;
    private final AlertWorkflowService workflow;

    public SecurityAlertController(SecurityAlertRepository repository, AlertWorkflowService workflow) {
        this.repository = repository;
        this.workflow = workflow;
    }

    @GetMapping
    public List<SecurityAlertDto> all() {
        return repository.findAll().stream().map(SecurityAlertDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SecurityAlertDto> byId(@PathVariable UUID id) {
        return repository.findById(id)
                .map(SecurityAlertDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SecurityAlertDto> create(@RequestBody SecurityAlertDto dto) {
        SecurityAlert saved = repository.save(dto.toEntity());
        return ResponseEntity.status(201).body(SecurityAlertDto.from(saved));
    }

    /** Moves the alert through OPEN → INVESTIGATING → RESOLVED / FALSE_POSITIVE. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> changeStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        try {
            AlertStatus status = AlertStatus.of(body.get("status"));
            return ResponseEntity.ok(SecurityAlertDto.from(workflow.changeStatus(id, status, body.get("actor"))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Applies a response action (HOLD_TRANSACTION, BLOCK_ACCOUNT, RATE_LIMIT, …). */
    @PostMapping("/{id}/action")
    public ResponseEntity<?> applyAction(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        try {
            AlertAction action = AlertAction.of(body.get("action"));
            return ResponseEntity.ok(SecurityAlertDto.from(workflow.applyAction(id, action, body.get("actor"))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}