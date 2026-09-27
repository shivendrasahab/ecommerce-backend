package com.shivendra.ecommerce.services.payment;

import com.shivendra.ecommerce.dtos.PaymentLinkResult;
import com.shivendra.ecommerce.models.Order;
import com.shivendra.ecommerce.models.PaymentProvider;

/**
 * Strategy interface: every supported gateway knows how to turn an order into a hosted checkout
 * link. Adding a gateway means adding one class, not touching the payment service.
 */
public interface PaymentGateway {

    PaymentProvider provider();

    /**
     * @param order       the order being paid for (amount, customer)
     * @param callbackUrl where the gateway sends the customer after payment; may be null
     */
    PaymentLinkResult createPaymentLink(Order order, String callbackUrl);
}
