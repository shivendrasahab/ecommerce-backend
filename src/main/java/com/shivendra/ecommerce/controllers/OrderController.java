package com.shivendra.ecommerce.controllers;

import com.shivendra.ecommerce.dtos.PlaceOrderRequestDto;
import com.shivendra.ecommerce.models.Order;
import com.shivendra.ecommerce.security.CurrentUser;
import com.shivendra.ecommerce.services.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** All routes require a Bearer token (enforced by AuthInterceptor). */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Converts the caller's cart into an order (PENDING_PAYMENT). */
    @PostMapping
    public ResponseEntity<Order> placeOrder(HttpServletRequest request, @Valid @RequestBody PlaceOrderRequestDto body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(CurrentUser.from(request), body));
    }

    @GetMapping
    public ResponseEntity<Page<Order>> listOrders(HttpServletRequest request,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(orderService.getOrders(CurrentUser.from(request), page, size));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(HttpServletRequest request, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrder(CurrentUser.from(request), orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Order> cancel(HttpServletRequest request, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(CurrentUser.from(request), orderId));
    }
}
