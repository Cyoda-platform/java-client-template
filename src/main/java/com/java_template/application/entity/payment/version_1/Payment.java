package com.java_template.application.entity.payment.version_1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Payment entity representing dummy payment processing for demonstration purposes.
 */
public class Payment implements CyodaEntity {

    @JsonProperty("paymentId")
    private String paymentId;

    @JsonProperty("cartId")
    private String cartId;

    @JsonProperty("amount")
    private Double amount;

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    // Default constructor
    public Payment() {}

    // Constructor with required fields
    public Payment(String paymentId, String cartId, Double amount, String provider) {
        this.paymentId = paymentId;
        this.cartId = cartId;
        this.amount = amount;
        this.provider = provider;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    @JsonIgnore
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("Payment");
        modelSpec.setVersion(1);
        return new OperationSpecification.Entity(modelSpec, "Payment");
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        return paymentId != null && !paymentId.trim().isEmpty() &&
               cartId != null && !cartId.trim().isEmpty() &&
               amount != null && amount > 0 &&
               provider != null && !provider.trim().isEmpty();
    }

    // Getters and setters
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getCartId() { return cartId; }
    public void setCartId(String cartId) { this.cartId = cartId; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // Helper method to update timestamp
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}
