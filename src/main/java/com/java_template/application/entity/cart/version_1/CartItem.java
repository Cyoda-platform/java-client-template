package com.java_template.application.entity.cart.version_1;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CartItem {
    private String productId; // UUID referencing Product
    private Integer quantity;
    private BigDecimal unitPrice; // snapshot of price at add time

    public CartItem() {}
}
