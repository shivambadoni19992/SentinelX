package com.sentinelx.retail.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sentinelx.retail.dto.OrderItemDto;
import com.sentinelx.retail.entity.OrderItem;
import com.sentinelx.retail.repository.OrderItemRepository;

@RestController
@RequestMapping("/api/retail/order-items")
public class OrderItemController {

    private final OrderItemRepository repository;

    public OrderItemController(OrderItemRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<OrderItemDto> all() {
        return repository.findAll().stream().map(OrderItemDto::from).toList();
    }

    @PostMapping
    public ResponseEntity<OrderItemDto> create(@RequestBody OrderItemDto dto) {
        OrderItem saved = repository.save(dto.toEntity());
        return ResponseEntity.status(201).body(OrderItemDto.from(saved));
    }
}