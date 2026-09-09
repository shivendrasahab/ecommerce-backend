package com.shivendra.ecommerce.dtos;

import com.shivendra.ecommerce.models.PaymentProvider;
import com.shivendra.ecommerce.models.PaymentStatus;

public record PaymentResponseDto(Long paymentId, Long orderId, PaymentProvider provider,
                                 String paymentLink, Double amount, PaymentStatus status) {
}
