package com.shivendra.ecommerce.services;

import com.shivendra.ecommerce.dtos.PlaceOrderRequestDto;
import com.shivendra.ecommerce.exceptions.BadRequestException;
import com.shivendra.ecommerce.exceptions.InsufficientStockException;
import com.shivendra.ecommerce.exceptions.ResourceNotFoundException;
import com.shivendra.ecommerce.models.Cart;
import com.shivendra.ecommerce.models.CartItem;
import com.shivendra.ecommerce.models.Order;
import com.shivendra.ecommerce.models.OrderItem;
import com.shivendra.ecommerce.models.OrderStatus;
import com.shivendra.ecommerce.models.Product;
import com.shivendra.ecommerce.models.User;
import com.shivendra.ecommerce.repositories.OrderRepository;
import com.shivendra.ecommerce.repositories.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Converts a cart into an order inside one transaction: validate stock for every line, decrement
 * stock, snapshot unit prices, empty the cart. Any failure rolls the whole thing back so stock is
 * never half-reserved.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        CartService cartService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.cartService = cartService;
    }

    @Transactional
    public Order placeOrder(User user, PlaceOrderRequestDto request) {
        Cart cart = cartService.getOrCreateCart(user);
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cannot place an order with an empty cart");
        }

        Order order = new Order();
        order.setUser(user);
        order.setShippingAddress(request.getShippingAddress());
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        double total = 0;
        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            int quantity = cartItem.getQuantity();
            if (product.getStock() < quantity) {
                throw new InsufficientStockException("Only " + product.getStock() + " unit(s) of '"
                        + product.getTitle() + "' left, cart has " + quantity);
            }
            product.setStock(product.getStock() - quantity);
            productRepository.save(product);

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setUnitPrice(product.getPrice());     // snapshot, not a reference
            order.getItems().add(item);
            total += product.getPrice() * quantity;
        }
        order.setTotalAmount(round2(total));

        Order saved = orderRepository.save(order);
        cartService.clearCart(user);
        return saved;
    }

    public Order getOrder(User user, Long orderId) {
        return orderRepository.findByIdAndUser(orderId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + orderId + " not found"));
    }

    public Page<Order> getOrders(User user, int pageNumber, int pageSize) {
        return orderRepository.findByUser(user,
                PageRequest.of(pageNumber, pageSize, Sort.by("createdAt").descending()));
    }

    /** Only unpaid orders can be cancelled by the customer; stock is returned to the shelf. */
    @Transactional
    public Order cancelOrder(User user, Long orderId) {
        Order order = getOrder(user, orderId);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BadRequestException("Order in status " + order.getStatus() + " cannot be cancelled");
        }
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStock(product.getStock() + item.getQuantity());
            productRepository.save(product);
        }
        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    /** Called by the payment flow once the gateway confirms money has moved. */
    @Transactional
    public Order markPaid(Order order) {
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            order.setStatus(OrderStatus.PAID);
            return orderRepository.save(order);
        }
        return order;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
