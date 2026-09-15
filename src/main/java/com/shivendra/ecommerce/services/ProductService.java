package com.shivendra.ecommerce.services;

import com.shivendra.ecommerce.dtos.ProductRequestDto;
import com.shivendra.ecommerce.models.Product;
import org.springframework.data.domain.Page;

/**
 * Catalogue operations. Two implementations exist: {@code SelfProductService} (own database,
 * the default) and {@code FakeStoreProductService} (proxy to the public FakeStore API).
 * Which one is wired is decided by the {@code product.source} property.
 */
public interface ProductService {

    Product getProductById(Long id);

    Page<Product> getAllProducts(int pageNumber, int pageSize, String sortBy, String direction);

    Page<Product> getProductsByCategory(Long categoryId, int pageNumber, int pageSize);

    Product createProduct(ProductRequestDto request);

    Product updateProduct(Long id, ProductRequestDto request);

    void deleteProduct(Long id);
}
