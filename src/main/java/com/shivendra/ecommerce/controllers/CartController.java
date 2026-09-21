package com.shivendra.ecommerce.controllers;

import com.shivendra.ecommerce.dtos.CartItemRequestDto;
import com.shivendra.ecommerce.models.Cart;
import com.shivendra.ecommerce.security.CurrentUser;
import com.shivendra.ecommerce.services.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** All routes require a Bearer token (enforced by AuthInterceptor). */
@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<Cart> getCart(HttpServletRequest request) {
        return ResponseEntity.ok(cartService.getOrCreateCart(CurrentUser.from(request)));
    }

    @PostMapping("/items")
    public ResponseEntity<Cart> addItem(HttpServletRequest request, @Valid @RequestBody CartItemRequestDto body) {
        return ResponseEntity.ok(cartService.addItem(CurrentUser.from(request), body));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<Cart> updateItem(HttpServletRequest request,
                                           @PathVariable Long productId,
                                           @RequestParam int quantity) {
        return ResponseEntity.ok(cartService.updateItem(CurrentUser.from(request), productId, quantity));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Cart> removeItem(HttpServletRequest request, @PathVariable Long productId) {
        return ResponseEntity.ok(cartService.removeItem(CurrentUser.from(request), productId));
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(HttpServletRequest request) {
        cartService.clearCart(CurrentUser.from(request));
        return ResponseEntity.noContent().build();
    }
}
