package com.sentinelx.auth.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sentinelx.auth.dto.SessionDto;
import com.sentinelx.auth.entity.Session;
import com.sentinelx.auth.repository.SessionRepository;

@RestController
@RequestMapping("/api/auth/sessions")
public class SessionController {

    private final SessionRepository repository;

    public SessionController(SessionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<SessionDto> all() {
        return repository.findAll().stream().map(SessionDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionDto> byId(@PathVariable UUID id) {
        return repository.findById(id)
                .map(SessionDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SessionDto> create(@RequestBody SessionDto dto) {
        Session saved = repository.save(dto.toEntity());
        return ResponseEntity.status(201).body(SessionDto.from(saved));
    }
}