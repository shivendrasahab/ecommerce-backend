package com.shivendra.ecommerce.services.payment;

import com.shivendra.ecommerce.dtos.InitiatePaymentRequestDto;
import com.shivendra.ecommerce.dtos.PaymentLinkResult;
import com.shivendra.ecommerce.dtos.PaymentResponseDto;
import com.shivendra.ecommerce.dtos.PaymentWebhookDto;
import com.shivendra.ecommerce.exceptions.BadRequestException;
import com.shivendra.ecommerce.models.Order;
import com.shivendra.ecommerce.models.OrderStatus;
import com.shivendra.ecommerce.models.Payment;
import com.shivendra.ecommerce.models.PaymentProvider;
import com.shivendra.ecommerce.models.PaymentStatus;
import com.shivendra.ecommerce.models.User;
import com.shivendra.ecommerce.repositories.PaymentRepository;
import com.shivendra.ecommerce.services.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentGateway razorpay;
    @Mock
    private PaymentGateway stripe;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderService orderService;

    private PaymentService paymentService;
    private User user;
    private Order order;

    @BeforeEach
    void setUp() {
        when(razorpay.provider()).thenReturn(PaymentProvider.RAZORPAY);
        when(stripe.provider()).thenReturn(PaymentProvider.STRIPE);
        paymentService = new PaymentService(List.of(razorpay, stripe), paymentRepository, orderService,
                PaymentProvider.RAZORPAY);
        user = new User();
        order = new Order();
        order.setId(5L);
        order.setTotalAmount(499.0);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void usesDefaultProviderWhenNoneRequested() {
        when(orderService.getOrder(user, 5L)).thenReturn(order);
        when(razorpay.createPaymentLink(order, null)).thenReturn(new PaymentLinkResult("plink_1", "https://rzp.io/x"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        InitiatePaymentRequestDto request = new InitiatePaymentRequestDto();
        request.setOrderId(5L);
        PaymentResponseDto response = paymentService.initiatePayment(user, request);

        assertThat(response.provider()).isEqualTo(PaymentProvider.RAZORPAY);
        assertThat(response.paymentLink()).isEqualTo("https://rzp.io/x");
        assertThat(response.status()).isEqualTo(PaymentStatus.INITIATED);
        verify(stripe, never()).createPaymentLink(any(), any());
    }

    @Test
    void routesToRequestedProvider() {
        when(orderService.getOrder(user, 5L)).thenReturn(order);
        when(stripe.createPaymentLink(order, "https://cb")).thenReturn(new PaymentLinkResult("cs_1", "https://stripe/x"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        InitiatePaymentRequestDto request = new InitiatePaymentRequestDto();
        request.setOrderId(5L);
        request.setProvider(PaymentProvider.STRIPE);
        request.setCallbackUrl("https://cb");

        assertThat(paymentService.initiatePayment(user, request).provider()).isEqualTo(PaymentProvider.STRIPE);
        verify(razorpay, never()).createPaymentLink(any(), any());
    }

    @Test
    void cannotPayForNonPendingOrder() {
        order.setStatus(OrderStatus.PAID);
        when(orderService.getOrder(user, 5L)).thenReturn(order);
        InitiatePaymentRequestDto request = new InitiatePaymentRequestDto();
        request.setOrderId(5L);

        assertThatThrownBy(() -> paymentService.initiatePayment(user, request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void successWebhookMarksOrderPaidOnce() {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider(PaymentProvider.RAZORPAY);
        payment.setAmount(499.0);
        payment.setStatus(PaymentStatus.INITIATED);
        when(paymentRepository.findByProviderReferenceId("plink_1")).thenReturn(Optional.of(payment));

        PaymentWebhookDto webhook = new PaymentWebhookDto();
        webhook.setProviderReferenceId("plink_1");
        webhook.setStatus(PaymentStatus.SUCCESS);

        paymentService.handleWebhook(webhook);
        paymentService.handleWebhook(webhook);   // duplicate delivery

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(orderService).markPaid(order);   // exactly once
    }

    @Test
    void failedWebhookDoesNotTouchOrder() {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider(PaymentProvider.STRIPE);
        payment.setAmount(499.0);
        when(paymentRepository.findByProviderReferenceId("cs_1")).thenReturn(Optional.of(payment));

        PaymentWebhookDto webhook = new PaymentWebhookDto();
        webhook.setProviderReferenceId("cs_1");
        webhook.setStatus(PaymentStatus.FAILED);
        paymentService.handleWebhook(webhook);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(orderService, never()).markPaid(any());
    }
}
