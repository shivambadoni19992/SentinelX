package com.sentinelx.alert.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sentinelx.alert.dto.SecurityAlertDto;
import com.sentinelx.alert.entity.SecurityAlert;
import com.sentinelx.alert.repository.SecurityAlertRepository;

@RestController
@RequestMapping("/api/alerts")
public class SecurityAlertController {

    private final SecurityAlertRepository repository;

    public SecurityAlertController(SecurityAlertRepository repository) {
        this.repository = repository;
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
}