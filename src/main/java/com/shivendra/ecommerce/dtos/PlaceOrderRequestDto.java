package com.shivendra.ecommerce.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlaceOrderRequestDto {

    @NotBlank
    private String shippingAddress;
}
