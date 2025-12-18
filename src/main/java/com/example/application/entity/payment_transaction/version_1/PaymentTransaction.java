package com.example.application.entity.payment_transaction.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PaymentTransaction Entity - Core payment processing entity
 * Supports multi-currency transactions, fraud detection, and PCI compliance
 */
@Data
public class PaymentTransaction implements CyodaEntity {
    public static final String ENTITY_NAME = "PaymentTransaction";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique transaction reference
    private String transactionId;

    // Core payment fields
    private String merchantId;
    private String customerId;
    private BigDecimal amount;
    private String currency; // USD, EUR, GBP, JPY, AUD
    private String description;

    // Tokenization & PCI Compliance
    private String cardToken; // Tokenized card, never plaintext
    private String cardLast4;
    private String cardBrand;
    private String cardExpiryMonth;
    private String cardExpiryYear;

    // Multi-currency support
    private BigDecimal exchangeRate;
    private String baseCurrency;
    private BigDecimal baseAmount;

    // Fraud detection metadata
    private String deviceFingerprint;
    private String ipAddress;
    private String geoLocation;
    private Double fraudScore;
    private String fraudStatus; // PENDING, APPROVED, FLAGGED, BLOCKED
    private String fraudReason;

    // Transaction status
    private String status; // PENDING, AUTHORIZED, CAPTURED, FAILED, REFUNDED
    private String processorReference;
    private String processorStatus;

    // Audit & Compliance
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime processedAt;
    private String processedBy;
    private List<AuditLog> auditLogs;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec()
                .withName(ENTITY_NAME)
                .withVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return transactionId != null && !transactionId.isBlank() &&
               merchantId != null && !merchantId.isBlank() &&
               amount != null && amount.compareTo(BigDecimal.ZERO) > 0 &&
               currency != null && !currency.isBlank() &&
               cardToken != null && !cardToken.isBlank();
    }

    @Data
    public static class AuditLog {
        private LocalDateTime timestamp;
        private String action;
        private String actor;
        private String details;
    }
}

