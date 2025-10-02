package com.java_template.application.entity.payment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ABOUTME: This file contains the Payment entity representing borrower remittances
 * that are captured, matched to loans, allocated to interest/fees/principal, and posted.
 */
@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = Payment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String paymentId;
    
    // Payment details
    private BigDecimal amount;
    private LocalDate valueDate;
    private String currency;
    private String sourceReference;
    private String paymentMethod; // BANK_TRANSFER, CHECK, WIRE, etc.
    
    // Loan matching information
    private String loanId;
    private String matchingReference;
    private String virtualAccountReference;
    
    // Allocation details
    private PaymentAllocation allocation;
    
    // Source information
    private String paymentFileId; // Reference to PaymentFile if imported
    private String sourceType; // MANUAL, FILE_IMPORT
    
    // Processing metadata
    private LocalDateTime capturedAt;
    private String capturedBy;
    private LocalDateTime matchedAt;
    private LocalDateTime allocatedAt;
    private LocalDateTime postedAt;
    
    // Status and metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return paymentId != null && !paymentId.trim().isEmpty() &&
               amount != null && amount.compareTo(BigDecimal.ZERO) > 0 &&
               valueDate != null &&
               currency != null && !currency.trim().isEmpty();
    }

    /**
     * Nested class for payment allocation details
     */
    @Data
    public static class PaymentAllocation {
        private BigDecimal interestAmount;
        private BigDecimal feesAmount;
        private BigDecimal principalAmount;
        private BigDecimal totalAllocated;
        private BigDecimal unallocatedAmount;
        private String allocationNotes;
    }
}
