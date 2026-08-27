package com.sentinelx.risk.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sentinelx.risk.dto.RiskDecisionDto;
import com.sentinelx.risk.entity.RiskDecision;
import com.sentinelx.risk.repository.RiskDecisionRepository;

@RestController
@RequestMapping("/api/risk/decisions")
public class RiskDecisionController {

    private final RiskDecisionRepository repository;

    public RiskDecisionController(RiskDecisionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<RiskDecisionDto> all() {
        return repository.findAll().stream().map(RiskDecisionDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RiskDecisionDto> byId(@PathVariable UUID id) {
        return repository.findById(id)
                .map(RiskDecisionDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<RiskDecisionDto> create(@RequestBody RiskDecisionDto dto) {
        RiskDecision saved = repository.save(dto.toEntity());
        return ResponseEntity.status(201).body(RiskDecisionDto.from(saved));
    }
}