package com.shivendra.ecommerce.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductRequestDto {

    @NotBlank
    private String title;

    private String description;

    @NotNull
    @Positive
    private Double price;

    private String imageUrl;

    @NotNull
    @Min(0)
    private Integer stock;

    /** Category is matched by name and created on the fly if it does not exist. */
    @NotBlank
    private String category;
}
