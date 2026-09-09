package com.shivendra.ecommerce.dtos;

import com.shivendra.ecommerce.models.PaymentProvider;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InitiatePaymentRequestDto {

    @NotNull
    private Long orderId;

    /** Optional; falls back to payment.default-provider. */
    private PaymentProvider provider;

    /** Where the gateway should send the customer after paying. */
    private String callbackUrl;
}
