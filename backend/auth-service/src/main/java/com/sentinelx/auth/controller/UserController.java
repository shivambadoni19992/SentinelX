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
import com.sentinelx.auth.dto.UserDto;
import com.sentinelx.auth.entity.User;
import com.sentinelx.auth.repository.UserRepository;

/**
 * Read/list/create endpoints for Users. Only DTOs cross the HTTP boundary.
 */
@RestController
@RequestMapping("/api/auth/users")
public class UserController {

    private final UserRepository repository;

    public UserController(UserRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<UserDto> all() {
        return repository.findAll().stream().map(UserDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> byId(@PathVariable UUID id) {
        return repository.findById(id)
                .map(UserDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@RequestBody UserDto dto) {
        User saved = repository.save(dto.toEntity());
        return ResponseEntity.status(201).body(UserDto.from(saved));
    }
}