package com.java_template.application.entity.payment.version_1;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PaymentRecord {
    private UUID id;
    private String orderId;
    private String provider;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String transactionId;
    private String createdAt;

    public PaymentRecord() {
    }
}
