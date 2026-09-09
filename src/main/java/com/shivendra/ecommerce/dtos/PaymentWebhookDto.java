package com.shivendra.ecommerce.dtos;

import com.shivendra.ecommerce.models.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Simplified webhook payload: the gateway tells us which reference finished and how.
 * Real integrations would also verify the gateway's signature header before trusting this.
 */
@Getter
@Setter
public class PaymentWebhookDto {

    @NotBlank
    private String providerReferenceId;

    @NotNull
    private PaymentStatus status;
}
