package com.sentinelx.retail.controller;

import java.util.List;
import java.util.UUID;

import com.sentinelx.retail.dto.ProductDto;
import com.sentinelx.retail.entity.Product;
import com.sentinelx.retail.repository.ProductRepository;
import com.sentinelx.retail.security.AuthenticatedRequestFilter;
import com.sentinelx.retail.security.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Product catalog API.
 *
 * <pre>
 *   GET  /api/retail/products          browse (any authenticated caller)
 *   GET  /api/retail/products/{id}     fetch
 *   POST /api/retail/products          create (privileged roles only)
 * </pre>
 */
@RestController
@RequestMapping("/api/retail/products")
public class ProductController {

    private final ProductRepository repository;

    public ProductController(ProductRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ProductDto> all(@RequestParam(name = "category", required = false) String category) {
        if (StringUtils.hasText(category)) {
            return repository.findByCategory(category).stream().map(ProductDto::from).toList();
        }
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
    public ResponseEntity<ProductDto> create(@Valid @RequestBody ProductDto dto, HttpServletRequest http) {
        requirePrivileged(http);
        if (dto.sku() == null || dto.sku().isBlank() || dto.name() == null || dto.name().isBlank()
                || dto.price() == null || dto.price().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sku, name and a non-negative price are required");
        }
        Product saved = repository.save(dto.toEntity());
        return ResponseEntity.status(201).body(ProductDto.from(saved));
    }

    static void requirePrivileged(HttpServletRequest http) {
        RequestContext caller = AuthenticatedRequestFilter.contextOf(http);
        if (!caller.privileged()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Privileged role required");
        }
    }
}