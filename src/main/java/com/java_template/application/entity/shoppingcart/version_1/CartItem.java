package com.java_template.application.entity.shoppingcart.version_1;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItem {
    private String productId; // serialized UUID
    private Integer quantity;
    private BigDecimal price;

    public CartItem() {
        this.price = BigDecimal.ZERO;
        this.quantity = 0;
    }

    public boolean isValid() {
        if (productId == null || productId.isBlank()) return false;
        if (quantity == null || quantity <= 0) return false;
        if (price == null || price.doubleValue() < 0) return false;
        return true;
    }
}
