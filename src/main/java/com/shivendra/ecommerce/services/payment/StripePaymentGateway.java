package com.shivendra.ecommerce.services.payment;

import com.shivendra.ecommerce.dtos.PaymentLinkResult;
import com.shivendra.ecommerce.exceptions.PaymentGatewayException;
import com.shivendra.ecommerce.models.Order;
import com.shivendra.ecommerce.models.OrderItem;
import com.shivendra.ecommerce.models.PaymentProvider;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stripe Checkout Sessions. One line item per order line so the customer sees an itemised bill on
 * Stripe's hosted page. Amounts are in the smallest currency unit (paise for INR).
 */
@Component
public class StripePaymentGateway implements PaymentGateway {

    private final String apiKey;

    public StripePaymentGateway(@Value("${payment.stripe.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @PostConstruct
    void init() {
        Stripe.apiKey = apiKey;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public PaymentLinkResult createPaymentLink(Order order, String callbackUrl) {
        String successUrl = (callbackUrl == null || callbackUrl.isBlank())
                ? "https://example.com/payment/success" : callbackUrl;

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(successUrl)
                .setCustomerEmail(order.getUser().getEmail())
                .setClientReferenceId(String.valueOf(order.getId()));

        for (OrderItem item : order.getItems()) {
            builder.addLineItem(SessionCreateParams.LineItem.builder()
                    .setQuantity((long) item.getQuantity())
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency("inr")
                            .setUnitAmount(Math.round(item.getUnitPrice() * 100))
                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(item.getProduct().getTitle())
                                    .build())
                            .build())
                    .build());
        }

        try {
            Session session = Session.create(builder.build());
            return new PaymentLinkResult(session.getId(), session.getUrl());
        } catch (StripeException e) {
            throw new PaymentGatewayException("Stripe rejected the checkout session request", e);
        }
    }
}
