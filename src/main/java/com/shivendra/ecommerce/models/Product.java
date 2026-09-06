package com.shivendra.ecommerce.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_title", columnList = "title"),
        @Index(name = "idx_products_category", columnList = "category_id"),
        @Index(name = "idx_products_price", columnList = "price")
})
@Getter
@Setter
public class Product extends BaseModel implements Serializable {

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private Double price;

    private String imageUrl;

    /** Units available for sale. Decremented when an order is placed, restored on cancellation. */
    @Column(nullable = false)
    private Integer stock = 0;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    /** Set when the product was imported from FakeStore, so re-imports are idempotent. */
    private Long externalId;
}
