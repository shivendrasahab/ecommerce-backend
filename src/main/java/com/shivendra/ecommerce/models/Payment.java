package com.shivendra.ecommerce.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** One payment attempt against an order, routed through a specific gateway. */
@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payments_order", columnList = "order_id"))
@Getter
@Setter
public class Payment extends BaseModel {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    /** Gateway-side identifier (Razorpay payment-link id, Stripe session id ...). */
    private String providerReferenceId;

    /** Hosted checkout URL the customer is redirected to. */
    @Column(length = 1000)
    private String paymentLink;

    @Column(nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.INITIATED;
}
