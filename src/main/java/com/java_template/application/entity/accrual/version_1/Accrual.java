package com.java_template.application.entity.accrual.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: This entity records the result of the daily interest calculation
 * for each active loan, creating an immutable audit trail of interest accruals.
 */
@Data
public class Accrual implements CyodaEntity {
    public static final String ENTITY_NAME = Accrual.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String accrualId;
    
    // Required core business fields
    private String loanId; // Reference to the Loan entity
    private LocalDate valueDate; // Date for which interest is being accrued
    private BigDecimal principalBase; // Outstanding principal at start of day
    private BigDecimal effectiveRate; // APR used for calculation
    private String dayCountBasis; // e.g., "ACT/365", "ACT/360", "30/360"
    
    // Calculation details
    private AccrualCalculation calculation;
    
    // Result
    private BigDecimal accruedAmount; // Calculated interest amount (8 decimal precision)
    
    // Sub-ledger entries (populated after posting)
    private List<AccrualSubLedgerEntry> subLedgerEntries;
    
    // Audit information
    private AccrualAudit audit;

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
        return accrualId != null && !accrualId.trim().isEmpty() &&
               loanId != null && !loanId.trim().isEmpty() &&
               valueDate != null &&
               principalBase != null && principalBase.compareTo(BigDecimal.ZERO) >= 0 &&
               effectiveRate != null && effectiveRate.compareTo(BigDecimal.ZERO) > 0 &&
               dayCountBasis != null && !dayCountBasis.trim().isEmpty();
    }

    /**
     * Nested class for calculation details
     */
    @Data
    public static class AccrualCalculation {
        private String formula; // e.g., "principalBase * effectiveRate * dcf"
        private BigDecimal dayCountFraction; // Fraction of year for this day
        private Integer daysInYear; // 365, 360, etc.
        private Integer actualDays; // Actual days for ACT calculations
    }

    /**
     * Nested class for sub-ledger entries
     */
    @Data
    public static class AccrualSubLedgerEntry {
        private String entryId;
        private String account; // e.g., "Interest Receivable", "Interest Income"
        private String type; // "DEBIT" or "CREDIT"
        private BigDecimal amount;
    }

    /**
     * Nested class for audit information
     */
    @Data
    public static class AccrualAudit {
        private LocalDateTime createdAt;
        private String createdBy; // Usually "System/EOD_Processor"
        private LocalDateTime postedAt;
        private String postedBy;
    }
}
