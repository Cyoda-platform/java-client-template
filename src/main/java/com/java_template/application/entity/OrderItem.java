package com.java_template.application.entity;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderItem {
    private String productId;
    private Integer quantity;
    private BigDecimal priceAtPurchase;
}
