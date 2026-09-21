package com.shivendra.ecommerce.services;

import com.shivendra.ecommerce.dtos.CartItemRequestDto;
import com.shivendra.ecommerce.exceptions.InsufficientStockException;
import com.shivendra.ecommerce.exceptions.ResourceNotFoundException;
import com.shivendra.ecommerce.models.Cart;
import com.shivendra.ecommerce.models.Product;
import com.shivendra.ecommerce.models.User;
import com.shivendra.ecommerce.repositories.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private ProductService productService;
    @InjectMocks
    private CartService cartService;

    private User user;
    private Cart cart;
    private Product product;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        cart = new Cart();
        cart.setUser(user);
        product = new Product();
        product.setId(42L);
        product.setTitle("MacBook Air");
        product.setPrice(1000.0);
        product.setStock(3);
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void addingSameProductTwiceMergesQuantities() {
        when(productService.getProductById(42L)).thenReturn(product);

        cartService.addItem(user, request(42L, 1));
        Cart result = cartService.addItem(user, request(42L, 2));

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(result.getTotal()).isEqualTo(3000.0);
    }

    @Test
    void addingBeyondStockIsRejected() {
        when(productService.getProductById(42L)).thenReturn(product);

        assertThatThrownBy(() -> cartService.addItem(user, request(42L, 4)))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void updateToZeroRemovesLine() {
        when(productService.getProductById(42L)).thenReturn(product);
        cartService.addItem(user, request(42L, 2));

        Cart result = cartService.updateItem(user, 42L, 0);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void updatingMissingLineIs404() {
        assertThatThrownBy(() -> cartService.updateItem(user, 99L, 1))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static CartItemRequestDto request(Long productId, int quantity) {
        CartItemRequestDto dto = new CartItemRequestDto();
        dto.setProductId(productId);
        dto.setQuantity(quantity);
        return dto;
    }
}
