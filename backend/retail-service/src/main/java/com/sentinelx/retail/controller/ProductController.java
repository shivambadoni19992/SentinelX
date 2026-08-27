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
import com.sentinelx.retail.dto.ProductDto;
import com.sentinelx.retail.entity.Product;
import com.sentinelx.retail.repository.ProductRepository;

@RestController
@RequestMapping("/api/retail/products")
public class ProductController {

    private final ProductRepository repository;

    public ProductController(ProductRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ProductDto> all() {
        return repository.findAll().stream().map(ProductDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> byId(@PathVariable UUID id) {
        return repository.findById(id)
                .map(ProductDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ProductDto> create(@RequestBody ProductDto dto) {
        Product saved = repository.save(dto.toEntity());
        return ResponseEntity.status(201).body(ProductDto.from(saved));
    }
}