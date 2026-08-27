package com.sentinelx.simulation.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sentinelx.simulation.dto.SimulationRunDto;
import com.sentinelx.simulation.entity.SimulationRun;
import com.sentinelx.simulation.repository.SimulationRunRepository;

@RestController
@RequestMapping("/api/simulations")
public class SimulationRunController {

    private final SimulationRunRepository repository;

    public SimulationRunController(SimulationRunRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<SimulationRunDto> all() {
        return repository.findAll().stream().map(SimulationRunDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SimulationRunDto> byId(@PathVariable UUID id) {
        return repository.findById(id)
                .map(SimulationRunDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SimulationRunDto> create(@RequestBody SimulationRunDto dto) {
        SimulationRun saved = repository.save(dto.toEntity());
        return ResponseEntity.status(201).body(SimulationRunDto.from(saved));
    }
}