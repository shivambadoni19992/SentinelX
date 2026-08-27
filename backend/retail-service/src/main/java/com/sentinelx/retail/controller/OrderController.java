package com.sentinelx.retail.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sentinelx.retail.dto.OrderDto;
import com.sentinelx.retail.entity.Order;
import com.sentinelx.retail.repository.OrderRepository;

@RestController
@RequestMapping("/api/retail/orders")
public class OrderController {

    private final OrderRepository repository;

    public OrderController(OrderRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<OrderDto> all() {
        return repository.findAll().stream().map(OrderDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> byId(@PathVariable UUID id) {
        return repository.findById(id)
                .map(OrderDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<OrderDto> create(@RequestBody OrderDto dto) {
        Order saved = repository.save(dto.toEntity());
        return ResponseEntity.status(201).body(OrderDto.from(saved));
    }
}