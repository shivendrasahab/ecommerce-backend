package com.shivendra.ecommerce.services;

import com.shivendra.ecommerce.dtos.CartItemRequestDto;
import com.shivendra.ecommerce.exceptions.InsufficientStockException;
import com.shivendra.ecommerce.exceptions.ResourceNotFoundException;
import com.shivendra.ecommerce.models.Cart;
import com.shivendra.ecommerce.models.CartItem;
import com.shivendra.ecommerce.models.Product;
import com.shivendra.ecommerce.models.User;
import com.shivendra.ecommerce.repositories.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * One cart per user, created lazily on first use. Quantities are validated against live stock so a
 * customer cannot add more units than the warehouse holds.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductService productService;

    public CartService(CartRepository cartRepository, ProductService productService) {
        this.cartRepository = cartRepository;
        this.productService = productService;
    }

    @Transactional
    public Cart getOrCreateCart(User user) {
        return cartRepository.findByUser(user).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUser(user);
            return cartRepository.save(cart);
        });
    }

    /** Adds a product; if it is already in the cart the quantities are merged. */
    @Transactional
    public Cart addItem(User user, CartItemRequestDto request) {
        Cart cart = getOrCreateCart(user);
        Product product = productService.getProductById(request.getProductId());

        Optional<CartItem> existing = findItem(cart, product.getId());
        int newQuantity = existing.map(CartItem::getQuantity).orElse(0) + request.getQuantity();
        ensureStock(product, newQuantity);

        if (existing.isPresent()) {
            existing.get().setQuantity(newQuantity);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(newQuantity);
            cart.getItems().add(item);
        }
        return cartRepository.save(cart);
    }

    /** Sets an absolute quantity for a line; quantity 0 removes the line. */
    @Transactional
    public Cart updateItem(User user, Long productId, int quantity) {
        Cart cart = getOrCreateCart(user);
        CartItem item = findItem(cart, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " is not in the cart"));
        if (quantity <= 0) {
            cart.getItems().remove(item);          // orphanRemoval deletes the row
        } else {
            ensureStock(item.getProduct(), quantity);
            item.setQuantity(quantity);
        }
        return cartRepository.save(cart);
    }

    @Transactional
    public Cart removeItem(User user, Long productId) {
        return updateItem(user, productId, 0);
    }

    @Transactional
    public void clearCart(User user) {
        Cart cart = getOrCreateCart(user);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    private Optional<CartItem> findItem(Cart cart, Long productId) {
        return cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .findFirst();
    }

    private void ensureStock(Product product, int requested) {
        if (product.getStock() == null || product.getStock() < requested) {
            throw new InsufficientStockException("Only " + product.getStock() + " unit(s) of '"
                    + product.getTitle() + "' available, requested " + requested);
        }
    }
}
