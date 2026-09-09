package com.shivendra.ecommerce.dtos;

/** What a payment gateway hands back after creating a hosted checkout. */
public record PaymentLinkResult(String providerReferenceId, String paymentLink) {
}
