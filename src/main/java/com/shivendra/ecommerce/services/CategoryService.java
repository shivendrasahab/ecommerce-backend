package com.shivendra.ecommerce.services;

import com.shivendra.ecommerce.dtos.CategoryRequestDto;
import com.shivendra.ecommerce.exceptions.BadRequestException;
import com.shivendra.ecommerce.exceptions.ResourceNotFoundException;
import com.shivendra.ecommerce.models.Category;
import com.shivendra.ecommerce.repositories.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " not found"));
    }

    @Transactional
    public Category createCategory(CategoryRequestDto request) {
        categoryRepository.findByNameIgnoreCase(request.getName()).ifPresent(c -> {
            throw new BadRequestException("Category '" + request.getName() + "' already exists");
        });
        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        return categoryRepository.save(category);
    }

    /** Find-or-create by name; used when products arrive with a category string. */
    @Transactional
    public Category getOrCreateByName(String name) {
        return categoryRepository.findByNameIgnoreCase(name).orElseGet(() -> {
            Category category = new Category();
            category.setName(name);
            return categoryRepository.save(category);
        });
    }
}
