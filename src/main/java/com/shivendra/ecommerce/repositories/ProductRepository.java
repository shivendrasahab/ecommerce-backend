package com.shivendra.ecommerce.repositories;

import com.shivendra.ecommerce.models.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndDeletedFalse(Long id);

    Page<Product> findByDeletedFalse(Pageable pageable);

    Page<Product> findByCategoryIdAndDeletedFalse(Long categoryId, Pageable pageable);

    Optional<Product> findByExternalId(Long externalId);

    /** Case-insensitive search across title and description, paged and sortable by the caller. */
    @Query("SELECT p FROM Product p WHERE p.deleted = false AND "
            + "(LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) "
            + " OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> search(@Param("query") String query, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.deleted = false AND LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Product> findByTitleKeyword(@Param("keyword") String keyword);
}
