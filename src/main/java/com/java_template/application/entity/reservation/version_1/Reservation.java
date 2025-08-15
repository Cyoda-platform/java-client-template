package com.java_template.application.entity.reservation.version_1;

import lombok.Data;

@Data
public class Reservation {
    private String id;
    private String cartId;
    private String productId;
    private Integer quantity;
    private String reservedAt;
    private String expiresAt;
    private String status;

    public Reservation() {}
}
