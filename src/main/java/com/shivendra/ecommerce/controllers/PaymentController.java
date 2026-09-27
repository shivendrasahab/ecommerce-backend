package com.shivendra.ecommerce.controllers;

import com.shivendra.ecommerce.dtos.InitiatePaymentRequestDto;
import com.shivendra.ecommerce.dtos.PaymentResponseDto;
import com.shivendra.ecommerce.dtos.PaymentWebhookDto;
import com.shivendra.ecommerce.security.CurrentUser;
import com.shivendra.ecommerce.services.payment.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Authenticated. Returns a hosted checkout link for the given order. */
    @PostMapping("/initiate")
    public ResponseEntity<PaymentResponseDto> initiate(HttpServletRequest request,
                                                       @Valid @RequestBody InitiatePaymentRequestDto body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.initiatePayment(CurrentUser.from(request), body));
    }

    /** Authenticated. Payment attempts for one of the caller's orders. */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<PaymentResponseDto>> forOrder(HttpServletRequest request, @PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentsForOrder(CurrentUser.from(request), orderId));
    }

    /**
     * Public (excluded from AuthInterceptor): called by the gateway. In production this endpoint
     * would also verify the gateway signature header before trusting the body.
     */
    @PostMapping("/webhook")
    public ResponseEntity<PaymentResponseDto> webhook(@Valid @RequestBody PaymentWebhookDto body) {
        return ResponseEntity.ok(paymentService.handleWebhook(body));
    }
}
