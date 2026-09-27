package com.shivendra.ecommerce.services.payment;

import com.shivendra.ecommerce.dtos.InitiatePaymentRequestDto;
import com.shivendra.ecommerce.dtos.PaymentLinkResult;
import com.shivendra.ecommerce.dtos.PaymentResponseDto;
import com.shivendra.ecommerce.dtos.PaymentWebhookDto;
import com.shivendra.ecommerce.exceptions.BadRequestException;
import com.shivendra.ecommerce.exceptions.ResourceNotFoundException;
import com.shivendra.ecommerce.models.Order;
import com.shivendra.ecommerce.models.OrderStatus;
import com.shivendra.ecommerce.models.Payment;
import com.shivendra.ecommerce.models.PaymentProvider;
import com.shivendra.ecommerce.models.PaymentStatus;
import com.shivendra.ecommerce.models.User;
import com.shivendra.ecommerce.repositories.PaymentRepository;
import com.shivendra.ecommerce.services.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates payments: picks a gateway (strategy), records the attempt, and reconciles the
 * gateway's webhook with the order. Gateways register themselves simply by being Spring beans
 * that implement {@link PaymentGateway}.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final Map<PaymentProvider, PaymentGateway> gateways = new EnumMap<>(PaymentProvider.class);
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final PaymentProvider defaultProvider;

    public PaymentService(List<PaymentGateway> gatewayBeans,
                          PaymentRepository paymentRepository,
                          OrderService orderService,
                          @Value("${payment.default-provider:RAZORPAY}") PaymentProvider defaultProvider) {
        gatewayBeans.forEach(g -> gateways.put(g.provider(), g));
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.defaultProvider = defaultProvider;
    }

    @Transactional
    public PaymentResponseDto initiatePayment(User user, InitiatePaymentRequestDto request) {
        Order order = orderService.getOrder(user, request.getOrderId());
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Order " + order.getId() + " is " + order.getStatus()
                    + " and cannot be paid for");
        }

        PaymentProvider provider = request.getProvider() != null ? request.getProvider() : defaultProvider;
        PaymentGateway gateway = gateways.get(provider);
        if (gateway == null) {
            throw new BadRequestException("Payment provider " + provider + " is not configured");
        }

        PaymentLinkResult link = gateway.createPaymentLink(order, request.getCallbackUrl());

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider(provider);
        payment.setAmount(order.getTotalAmount());
        payment.setProviderReferenceId(link.providerReferenceId());
        payment.setPaymentLink(link.paymentLink());
        payment.setStatus(PaymentStatus.INITIATED);
        Payment saved = paymentRepository.save(payment);

        log.info("Payment {} initiated via {} for order {}", saved.getId(), provider, order.getId());
        return toDto(saved);
    }

    /**
     * Webhook reconciliation. Idempotent: a repeated SUCCESS for an already-paid order is a no-op.
     */
    @Transactional
    public PaymentResponseDto handleWebhook(PaymentWebhookDto webhook) {
        Payment payment = paymentRepository.findByProviderReferenceId(webhook.getProviderReferenceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No payment with reference " + webhook.getProviderReferenceId()));

        if (payment.getStatus() == PaymentStatus.INITIATED) {
            payment.setStatus(webhook.getStatus());
            paymentRepository.save(payment);
            if (webhook.getStatus() == PaymentStatus.SUCCESS) {
                orderService.markPaid(payment.getOrder());
            }
            log.info("Payment {} moved to {}", payment.getId(), webhook.getStatus());
        }
        return toDto(payment);
    }

    public List<PaymentResponseDto> getPaymentsForOrder(User user, Long orderId) {
        Order order = orderService.getOrder(user, orderId);
        return paymentRepository.findByOrder(order).stream().map(this::toDto).toList();
    }

    private PaymentResponseDto toDto(Payment p) {
        return new PaymentResponseDto(p.getId(), p.getOrder().getId(), p.getProvider(),
                p.getPaymentLink(), p.getAmount(), p.getStatus());
    }
}
