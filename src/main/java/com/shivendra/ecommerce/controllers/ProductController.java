package com.shivendra.ecommerce.controllers;

import com.shivendra.ecommerce.dtos.ProductRequestDto;
import com.shivendra.ecommerce.models.Product;
import com.shivendra.ecommerce.services.PopularProductCacheService;
import com.shivendra.ecommerce.services.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public catalogue API. Which {@link ProductService} backs it (own DB or FakeStore proxy) is a
 * configuration choice, not a code change.
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final PopularProductCacheService popularProductCache;

    public ProductController(ProductService productService, PopularProductCacheService popularProductCache) {
        this.productService = productService;
        this.popularProductCache = popularProductCache;
    }

    @GetMapping
    public ResponseEntity<Page<Product>> getAll(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(defaultValue = "id") String sortBy,
                                                @RequestParam(defaultValue = "asc") String direction) {
        return ResponseEntity.ok(productService.getAllProducts(page, size, sortBy, direction));
    }

    /** Served from Redis when warm; see PopularProductCacheService for the fallback story. */
    @GetMapping("/popular")
    public ResponseEntity<List<Product>> getPopular() {
        return ResponseEntity.ok(popularProductCache.getPopularProducts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<Page<Product>> getByCategory(@PathVariable Long categoryId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(productService.getProductsByCategory(categoryId, page, size));
    }

    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody ProductRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id, @Valid @RequestBody ProductRequestDto request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
