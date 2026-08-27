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
import com.sentinelx.auth.dto.DeviceDto;
import com.sentinelx.auth.entity.Device;
import com.sentinelx.auth.repository.DeviceRepository;

@RestController
@RequestMapping("/api/auth/devices")
public class DeviceController {

    private final DeviceRepository repository;

    public DeviceController(DeviceRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<DeviceDto> all() {
        return repository.findAll().stream().map(DeviceDto::from).toList();
    }

    @PostMapping
    public ResponseEntity<DeviceDto> create(@RequestBody DeviceDto dto) {
        Device saved = repository.save(dto.toEntity());
        return ResponseEntity.status(201).body(DeviceDto.from(saved));
    }
}