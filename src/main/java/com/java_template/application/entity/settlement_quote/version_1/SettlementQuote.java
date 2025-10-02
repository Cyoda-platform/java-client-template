package com.java_template.application.entity.settlement_quote.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ABOUTME: This file contains the SettlementQuote entity representing early payoff quotes
 * for loans with calculated amounts and acceptance tracking.
 */
@Data
public class SettlementQuote implements CyodaEntity {
    public static final String ENTITY_NAME = SettlementQuote.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String settlementQuoteId;
    
    // Loan reference
    private String loanId;
    
    // Quote details
    private LocalDate asOfDate; // Settlement date
    private LocalDate expiryDate; // Quote expiry
    private LocalDate quotedDate; // When quote was generated
    
    // Calculated amounts
    private BigDecimal outstandingPrincipal;
    private BigDecimal accruedInterest;
    private BigDecimal settlementFee;
    private BigDecimal totalPayoffAmount;
    
    // Quote breakdown
    private SettlementBreakdown breakdown;
    
    // Acceptance information
    private LocalDateTime acceptedAt;
    private String acceptedBy;
    private String paymentId; // Reference to covering payment
    
    // Execution information
    private LocalDateTime executedAt;
    private LocalDateTime expiredAt;
    
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
        return settlementQuoteId != null && !settlementQuoteId.trim().isEmpty() &&
               loanId != null && !loanId.trim().isEmpty() &&
               asOfDate != null &&
               expiryDate != null &&
               totalPayoffAmount != null && totalPayoffAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Nested class for settlement breakdown details
     */
    @Data
    public static class SettlementBreakdown {
        private BigDecimal principalAsOf;
        private BigDecimal interestToDate;
        private BigDecimal feeCalculation;
        private String feePolicy; // NONE, FIXED, PERCENTAGE
        private BigDecimal feeRate;
        private String calculationNotes;
    }
}
