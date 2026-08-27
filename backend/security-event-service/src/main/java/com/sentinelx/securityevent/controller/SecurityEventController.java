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
import com.sentinelx.securityevent.dto.SecurityEventDto;
import com.sentinelx.securityevent.entity.SecurityEvent;
import com.sentinelx.securityevent.repository.SecurityEventRepository;

@RestController
@RequestMapping("/api/events")
public class SecurityEventController {

    private final SecurityEventRepository repository;

    public SecurityEventController(SecurityEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<SecurityEventDto> all() {
        return repository.findAll().stream().map(SecurityEventDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SecurityEventDto> byId(@PathVariable UUID id) {
        return repository.findById(id)
                .map(SecurityEventDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SecurityEventDto> create(@RequestBody SecurityEventDto dto) {
        SecurityEvent saved = repository.save(dto.toEntity());
        return ResponseEntity.status(201).body(SecurityEventDto.from(saved));
    }
}