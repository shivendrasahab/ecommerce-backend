package com.shivendra.ecommerce.services;

import com.shivendra.ecommerce.dtos.FakeStoreProductDto;
import com.shivendra.ecommerce.dtos.ProductRequestDto;
import com.shivendra.ecommerce.exceptions.ResourceNotFoundException;
import com.shivendra.ecommerce.models.Category;
import com.shivendra.ecommerce.models.Product;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Catalogue proxied to https://fakestoreapi.com. Useful for demos without seeding a database.
 * FakeStore does not persist writes, so create/update/delete are echoed back, not stored.
 */
@Service
@Qualifier("fakeStoreProductService")
@ConditionalOnProperty(name = "product.source", havingValue = "fakestore")
public class FakeStoreProductService implements ProductService {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public FakeStoreProductService(RestTemplate restTemplate,
                                   @Value("${fakestore.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public Product getProductById(Long id) {
        try {
            FakeStoreProductDto dto = restTemplate.getForObject(baseUrl + "/products/{id}", FakeStoreProductDto.class, id);
            if (dto == null || dto.getId() == null) {
                throw new ResourceNotFoundException("Product " + id + " not found");
            }
            return toProduct(dto);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Product " + id + " not found");
        }
    }

    @Override
    public Page<Product> getAllProducts(int pageNumber, int pageSize, String sortBy, String direction) {
        List<Product> all = fetchAll();
        Comparator<Product> comparator = "price".equalsIgnoreCase(sortBy)
                ? Comparator.comparing(Product::getPrice)
                : Comparator.comparing(Product::getTitle, String.CASE_INSENSITIVE_ORDER);
        if ("desc".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }
        all.sort(comparator);
        return slice(all, pageNumber, pageSize);
    }

    @Override
    public Page<Product> getProductsByCategory(Long categoryId, int pageNumber, int pageSize) {
        // FakeStore categories are strings; we map our numeric id onto its category list order.
        String[] categories = restTemplate.getForObject(baseUrl + "/products/categories", String[].class);
        if (categories == null || categoryId < 1 || categoryId > categories.length) {
            throw new ResourceNotFoundException("Category " + categoryId + " not found");
        }
        String category = categories[(int) (categoryId - 1)];
        FakeStoreProductDto[] dtos = restTemplate.getForObject(baseUrl + "/products/category/{c}", FakeStoreProductDto[].class, category);
        List<Product> products = new ArrayList<>();
        if (dtos != null) {
            Arrays.stream(dtos).map(this::toProduct).forEach(products::add);
        }
        return slice(products, pageNumber, pageSize);
    }

    @Override
    public Product createProduct(ProductRequestDto request) {
        FakeStoreProductDto body = fromRequest(request);
        FakeStoreProductDto created = restTemplate.postForObject(baseUrl + "/products", body, FakeStoreProductDto.class);
        return toProduct(created != null ? created : body);
    }

    @Override
    public Product updateProduct(Long id, ProductRequestDto request) {
        FakeStoreProductDto body = fromRequest(request);
        ResponseEntity<FakeStoreProductDto> response = restTemplate.exchange(
                baseUrl + "/products/{id}", HttpMethod.PUT, new HttpEntity<>(body), FakeStoreProductDto.class, id);
        FakeStoreProductDto updated = response.getBody();
        if (updated != null && updated.getId() == null) {
            updated.setId(id);
        }
        return toProduct(updated != null ? updated : body);
    }

    @Override
    public void deleteProduct(Long id) {
        restTemplate.delete(baseUrl + "/products/{id}", id);
    }

    // ---- helpers ----

    private List<Product> fetchAll() {
        FakeStoreProductDto[] dtos = restTemplate.getForObject(baseUrl + "/products", FakeStoreProductDto[].class);
        List<Product> products = new ArrayList<>();
        if (dtos != null) {
            Arrays.stream(dtos).map(this::toProduct).forEach(products::add);
        }
        return products;
    }

    private Page<Product> slice(List<Product> all, int pageNumber, int pageSize) {
        int from = Math.min(pageNumber * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return new PageImpl<>(all.subList(from, to), PageRequest.of(pageNumber, pageSize), all.size());
    }

    private Product toProduct(FakeStoreProductDto dto) {
        Product product = new Product();
        product.setId(dto.getId());
        product.setExternalId(dto.getId());
        product.setTitle(dto.getTitle());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setImageUrl(dto.getImage());
        product.setStock(10); // FakeStore has no inventory concept
        if (dto.getCategory() != null) {
            Category category = new Category();
            category.setName(dto.getCategory());
            product.setCategory(category);
        }
        return product;
    }

    private FakeStoreProductDto fromRequest(ProductRequestDto request) {
        FakeStoreProductDto dto = new FakeStoreProductDto();
        dto.setTitle(request.getTitle());
        dto.setDescription(request.getDescription());
        dto.setPrice(request.getPrice());
        dto.setImage(request.getImageUrl());
        dto.setCategory(request.getCategory());
        return dto;
    }
}
