package com.java_template.application.application.payment;

import java.math.BigDecimal;

public class PaymentGatewayClient {
    public boolean charge(String idempotencyKey, BigDecimal amount) {
        // For demo purposes always succeed when amount > 0
        if (amount == null) return false;
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }
}
