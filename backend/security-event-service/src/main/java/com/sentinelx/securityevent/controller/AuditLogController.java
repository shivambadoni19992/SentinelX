package com.sentinelx.securityevent.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sentinelx.securityevent.dto.AuditLogDto;
import com.sentinelx.securityevent.entity.AuditLog;
import com.sentinelx.securityevent.repository.AuditLogRepository;

@RestController
@RequestMapping("/api/events/audit-logs")
public class AuditLogController {

    private final AuditLogRepository repository;

    public AuditLogController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<AuditLogDto> all() {
        return repository.findAll().stream().map(AuditLogDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogDto> byId(@PathVariable UUID id) {
        return repository.findById(id)
                .map(AuditLogDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<AuditLogDto> create(@RequestBody AuditLogDto dto) {
        AuditLog saved = repository.save(dto.toEntity());
        return ResponseEntity.status(201).body(AuditLogDto.from(saved));
    }
}