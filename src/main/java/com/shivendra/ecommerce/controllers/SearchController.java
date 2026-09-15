package com.shivendra.ecommerce.controllers;

import com.shivendra.ecommerce.models.Product;
import com.shivendra.ecommerce.services.SearchService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /search?q=iphone&page=0&size=10&sortBy=price&direction=desc */
@RestController
@RequestMapping("/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<Page<Product>> search(@RequestParam("q") String query,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(defaultValue = "title") String sortBy,
                                                @RequestParam(defaultValue = "asc") String direction) {
        return ResponseEntity.ok(searchService.search(query, page, size, sortBy, direction));
    }
}
