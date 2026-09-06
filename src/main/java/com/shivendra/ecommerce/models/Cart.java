package com.shivendra.ecommerce.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** One active cart per user. Items are owned by the cart (cascade + orphanRemoval). */
@Entity
@Table(name = "carts")
@Getter
@Setter
public class Cart extends BaseModel {

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<CartItem> items = new ArrayList<>();

    public double getTotal() {
        return items.stream().mapToDouble(i -> i.getProduct().getPrice() * i.getQuantity()).sum();
    }
}
