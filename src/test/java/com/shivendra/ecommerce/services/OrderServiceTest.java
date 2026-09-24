package com.shivendra.ecommerce.services;

import com.shivendra.ecommerce.dtos.PlaceOrderRequestDto;
import com.shivendra.ecommerce.exceptions.BadRequestException;
import com.shivendra.ecommerce.exceptions.InsufficientStockException;
import com.shivendra.ecommerce.models.Cart;
import com.shivendra.ecommerce.models.CartItem;
import com.shivendra.ecommerce.models.Order;
import com.shivendra.ecommerce.models.OrderItem;
import com.shivendra.ecommerce.models.OrderStatus;
import com.shivendra.ecommerce.models.Product;
import com.shivendra.ecommerce.models.User;
import com.shivendra.ecommerce.repositories.OrderRepository;
import com.shivendra.ecommerce.repositories.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CartService cartService;
    @InjectMocks
    private OrderService orderService;

    private User user;
    private Product phone;
    private Product shoes;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        phone = product(10L, "iPhone 15", 999.99, 5);
        shoes = product(20L, "Air Jordan 1", 150.00, 1);
    }

    @Test
    void placeOrderDecrementsStockSnapshotsPriceAndClearsCart() {
        Cart cart = cartWith(item(phone, 2), item(shoes, 1));
        when(cartService.getOrCreateCart(user)).thenReturn(cart);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        PlaceOrderRequestDto request = new PlaceOrderRequestDto();
        request.setShippingAddress("Bengaluru");
        Order order = orderService.placeOrder(user, request);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getTotalAmount()).isEqualTo(2149.98);
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getItems().get(0).getUnitPrice()).isEqualTo(999.99);
        assertThat(phone.getStock()).isEqualTo(3);
        assertThat(shoes.getStock()).isEqualTo(0);
        verify(cartService).clearCart(user);
    }

    @Test
    void placeOrderWithEmptyCartIsRejected() {
        when(cartService.getOrCreateCart(user)).thenReturn(cartWith());

        assertThatThrownBy(() -> orderService.placeOrder(user, new PlaceOrderRequestDto()))
                .isInstanceOf(BadRequestException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrderFailsWhenStockIsShort() {
        Cart cart = cartWith(item(shoes, 3));   // only 1 in stock
        when(cartService.getOrCreateCart(user)).thenReturn(cart);

        assertThatThrownBy(() -> orderService.placeOrder(user, new PlaceOrderRequestDto()))
                .isInstanceOf(InsufficientStockException.class);
        verify(orderRepository, never()).save(any());
        verify(cartService, never()).clearCart(any());
    }

    @Test
    void cancelRestoresStockOnlyForPendingOrders() {
        Order order = new Order();
        order.setId(7L);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        OrderItem item = new OrderItem();
        item.setProduct(phone);
        item.setQuantity(2);
        item.setUnitPrice(999.99);
        order.getItems().add(item);
        phone.setStock(3);
        when(orderRepository.findByIdAndUser(7L, user)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order cancelled = orderService.cancelOrder(user, 7L);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(phone.getStock()).isEqualTo(5);
    }

    @Test
    void cancelPaidOrderIsRejected() {
        Order order = new Order();
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByIdAndUser(7L, user)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(user, 7L)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void markPaidIsIdempotent() {
        Order order = new Order();
        order.setStatus(OrderStatus.PAID);

        assertThat(orderService.markPaid(order).getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository, never()).save(any());
    }

    private static Product product(Long id, String title, double price, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setTitle(title);
        p.setPrice(price);
        p.setStock(stock);
        return p;
    }

    private static CartItem item(Product product, int quantity) {
        CartItem i = new CartItem();
        i.setProduct(product);
        i.setQuantity(quantity);
        return i;
    }

    private Cart cartWith(CartItem... items) {
        Cart cart = new Cart();
        cart.setUser(user);
        for (CartItem i : items) {
            cart.getItems().add(i);
        }
        return cart;
    }
}
