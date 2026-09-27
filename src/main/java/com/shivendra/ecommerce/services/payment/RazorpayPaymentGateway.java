package com.shivendra.ecommerce.services.payment;

import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.shivendra.ecommerce.dtos.PaymentLinkResult;
import com.shivendra.ecommerce.exceptions.PaymentGatewayException;
import com.shivendra.ecommerce.models.Order;
import com.shivendra.ecommerce.models.PaymentProvider;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * Razorpay Payment Links. Amounts are sent in the smallest currency unit (paise), so INR 499.00
 * becomes 49900.
 */
@Component
public class RazorpayPaymentGateway implements PaymentGateway {

    private final RazorpayClient razorpayClient;

    public RazorpayPaymentGateway(RazorpayClient razorpayClient) {
        this.razorpayClient = razorpayClient;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.RAZORPAY;
    }

    @Override
    public PaymentLinkResult createPaymentLink(Order order, String callbackUrl) {
        JSONObject request = new JSONObject();
        request.put("amount", Math.round(order.getTotalAmount() * 100));
        request.put("currency", "INR");
        request.put("accept_partial", false);
        request.put("description", "Order #" + order.getId());
        request.put("reference_id", String.valueOf(order.getId()));

        JSONObject customer = new JSONObject();
        customer.put("name", order.getUser().getName());
        customer.put("email", order.getUser().getEmail());
        if (order.getUser().getPhoneNumber() != null) {
            customer.put("contact", order.getUser().getPhoneNumber());
        }
        request.put("customer", customer);

        JSONObject notify = new JSONObject();
        notify.put("email", true);
        notify.put("sms", order.getUser().getPhoneNumber() != null);
        request.put("notify", notify);
        request.put("reminder_enable", true);

        if (callbackUrl != null && !callbackUrl.isBlank()) {
            request.put("callback_url", callbackUrl);
            request.put("callback_method", "get");
        }

        try {
            PaymentLink link = razorpayClient.paymentLink.create(request);
            return new PaymentLinkResult(link.get("id"), link.get("short_url"));
        } catch (RazorpayException e) {
            throw new PaymentGatewayException("Razorpay rejected the payment link request", e);
        }
    }
}
