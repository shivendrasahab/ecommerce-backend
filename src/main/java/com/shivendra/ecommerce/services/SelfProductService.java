package com.shivendra.ecommerce.services;

import com.shivendra.ecommerce.dtos.ProductRequestDto;
import com.shivendra.ecommerce.exceptions.ResourceNotFoundException;
import com.shivendra.ecommerce.models.Product;
import com.shivendra.ecommerce.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogue backed by our own MySQL/H2 tables. Default implementation.
 */
@Service
@Primary
@Qualifier("selfProductService")
@ConditionalOnProperty(name = "product.source", havingValue = "self", matchIfMissing = true)
public class SelfProductService implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final PopularProductCacheService popularProductCache;

    public SelfProductService(ProductRepository productRepository,
                              CategoryService categoryService,
                              PopularProductCacheService popularProductCache) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.popularProductCache = popularProductCache;
    }

    @Override
    public Product getProductById(Long id) {
        return productRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " not found"));
    }

    @Override
    public Page<Product> getAllProducts(int pageNumber, int pageSize, String sortBy, String direction) {
        Sort sort = "desc".equalsIgnoreCase(direction)
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        return productRepository.findByDeletedFalse(pageable);
    }

    @Override
    public Page<Product> getProductsByCategory(Long categoryId, int pageNumber, int pageSize) {
        categoryService.getCategoryById(categoryId); // 404 if the category does not exist
        return productRepository.findByCategoryIdAndDeletedFalse(categoryId, PageRequest.of(pageNumber, pageSize));
    }

    @Override
    @Transactional
    public Product createProduct(ProductRequestDto request) {
        Product product = new Product();
        apply(product, request);
        Product saved = productRepository.save(product);
        popularProductCache.onProductChanged(saved);
        return saved;
    }

    @Override
    @Transactional
    public Product updateProduct(Long id, ProductRequestDto request) {
        Product product = getProductById(id);
        apply(product, request);
        Product saved = productRepository.save(product);
        popularProductCache.onProductChanged(saved);
        return saved;
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        product.setDeleted(true);          // soft delete keeps historical orders consistent
        productRepository.save(product);
        popularProductCache.onProductRemoved(product);
    }

    private void apply(Product product, ProductRequestDto request) {
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setImageUrl(request.getImageUrl());
        product.setStock(request.getStock());
        product.setCategory(categoryService.getOrCreateByName(request.getCategory()));
    }
}
