package com.shivendra.ecommerce.dtos;

import lombok.Getter;
import lombok.Setter;

/** Shape of https://fakestoreapi.com/products/{id}. */
@Getter
@Setter
public class FakeStoreProductDto {
    private Long id;
    private String title;
    private Double price;
    private String description;
    private String category;
    private String image;
}
