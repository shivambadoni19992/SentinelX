package com.sentinelx.payment.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sentinelx.payment.dto.PaymentDto;
import com.sentinelx.payment.entity.Payment;
import com.sentinelx.payment.repository.PaymentRepository;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentRepository repository;

    public PaymentController(PaymentRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<PaymentDto> all() {
        return repository.findAll().stream().map(PaymentDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentDto> byId(@PathVariable UUID id) {
        return repository.findById(id)
                .map(PaymentDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PaymentDto> create(@RequestBody PaymentDto dto) {
        Payment saved = repository.save(dto.toEntity());
        return ResponseEntity.status(201).body(PaymentDto.from(saved));
    }
}