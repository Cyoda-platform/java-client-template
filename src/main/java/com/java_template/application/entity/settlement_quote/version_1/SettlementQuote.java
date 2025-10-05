package com.java_template.application.entity.settlement_quote.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ABOUTME: This entity stores the details of a quote for an early loan settlement,
 * including the total amount due and the quote's expiration date.
 */
@Data
public class SettlementQuote implements CyodaEntity {
    public static final String ENTITY_NAME = SettlementQuote.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String quoteId;
    
    // Required core business fields
    private String loanId; // Reference to the Loan entity
    private LocalDate settlementDate; // Proposed settlement date
    private LocalDate expirationDate; // When this quote expires
    private String requestedBy; // User who requested the quote
    
    // Quote calculation details
    private SettlementCalculation calculation;
    
    // Total amount due
    private BigDecimal totalAmountDue;
    private String currency;

    // Audit information
    private SettlementAudit audit;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return quoteId != null && !quoteId.trim().isEmpty() &&
               loanId != null && !loanId.trim().isEmpty() &&
               settlementDate != null &&
               expirationDate != null &&
               requestedBy != null && !requestedBy.trim().isEmpty() &&
               totalAmountDue != null && totalAmountDue.compareTo(BigDecimal.ZERO) > 0 &&
               currency != null && !currency.trim().isEmpty();
    }

    /**
     * Nested class for settlement calculation breakdown
     */
    @Data
    public static class SettlementCalculation {
        private BigDecimal outstandingPrincipal;
        private BigDecimal accruedInterestToDate;
        private BigDecimal projectedInterestToSettlement;
        private BigDecimal fees;
        private BigDecimal breakCosts; // If applicable
        private BigDecimal totalInterest;
        private String calculationMethod;
        private String dayCountBasis;
    }

    /**
     * Nested class for audit information
     */
    @Data
    public static class SettlementAudit {
        private LocalDateTime createdAt;
        private String createdBy;
        private LocalDateTime quotedAt;
        private String quotedBy;
        private LocalDateTime acceptedAt;
        private String acceptedBy;
        private LocalDateTime expiredAt;
    }
}
