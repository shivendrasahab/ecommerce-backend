package com.shivendra.ecommerce.services;

import com.shivendra.ecommerce.exceptions.BadRequestException;
import com.shivendra.ecommerce.models.Product;
import com.shivendra.ecommerce.repositories.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Keyword search with user-controlled paging and sorting. The sortable fields are allow-listed
 * so a caller cannot sort by (or probe) arbitrary columns.
 */
@Service
public class SearchService {

    private static final Set<String> SORTABLE = Set.of("title", "price", "createdAt");
    private static final int MAX_PAGE_SIZE = 50;

    private final ProductRepository productRepository;

    public SearchService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<Product> search(String query, int pageNumber, int pageSize, String sortBy, String direction) {
        if (query == null || query.isBlank()) {
            throw new BadRequestException("Search query must not be empty");
        }
        if (!SORTABLE.contains(sortBy)) {
            throw new BadRequestException("Cannot sort by '" + sortBy + "'. Allowed: " + SORTABLE);
        }
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Sort sort = "desc".equalsIgnoreCase(direction) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(Math.max(pageNumber, 0), size, sort);
        return productRepository.search(query.trim(), pageable);
    }
}
