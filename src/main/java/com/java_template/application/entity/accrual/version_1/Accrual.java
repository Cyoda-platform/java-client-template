package com.java_template.application.entity.accrual.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ABOUTME: This file contains the Accrual entity representing daily interest calculations
 * per loan with precise calculation details and subledger integration.
 */
@Data
public class Accrual implements CyodaEntity {
    public static final String ENTITY_NAME = Accrual.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String accrualId;
    
    // Loan reference
    private String loanId;
    
    // Accrual date and calculation details
    private LocalDate accrualDate;
    private BigDecimal principalBase; // Principal amount used for calculation
    private BigDecimal apr; // Annual Percentage Rate used
    private String dayCountBasis; // ACT/365F, ACT/360, ACT/365L
    private Integer dayCountNumerator; // Actual days
    private Integer dayCountDenominator; // 365, 360, etc.
    
    // Calculated amounts
    private BigDecimal interestAmount; // Calculated interest (≥8 decimal places)
    private BigDecimal dailyRate; // Daily interest rate used
    
    // Recomputation tracking
    private Boolean isRecomputation;
    private String originalAccrualId; // Reference to original if this is a recomputation
    private String recomputationReason;
    
    // Processing metadata
    private LocalDateTime scheduledAt;
    private LocalDateTime calculatedAt;
    private LocalDateTime recordedAt;
    
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
        return accrualId != null && !accrualId.trim().isEmpty() &&
               loanId != null && !loanId.trim().isEmpty() &&
               accrualDate != null &&
               principalBase != null && principalBase.compareTo(BigDecimal.ZERO) >= 0 &&
               apr != null && apr.compareTo(BigDecimal.ZERO) > 0 &&
               dayCountBasis != null && !dayCountBasis.trim().isEmpty();
    }
}
