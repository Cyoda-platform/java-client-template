package com.java_template.application.entity.loan.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ABOUTME: Loan entity representing a corporate loan with lifecycle management.
 * Implements CyodaEntity for workflow integration and state management.
 */
@Data
public class Loan implements CyodaEntity {
    public static final String ENTITY_NAME = Loan.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String loanId;
    
    // Core loan information
    private String borrowerName;
    private String borrowerEmail;
    private String borrowerPhone;
    private BigDecimal loanAmount;
    private Integer loanTermMonths;
    private BigDecimal interestRate;
    private String loanPurpose;
    
    // Loan details
    private String collateral;
    private BigDecimal collateralValue;
    private String loanType; // e.g., "TERM_LOAN", "LINE_OF_CREDIT", "EQUIPMENT_FINANCING"
    
    // Dates
    private LocalDateTime applicationDate;
    private LocalDateTime approvalDate;
    private LocalDateTime disbursementDate;
    private LocalDateTime maturityDate;
    
    // Additional information
    private String notes;
    private String approverName;
    private String approverComments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
        return loanId != null && !loanId.trim().isEmpty() &&
               borrowerName != null && !borrowerName.trim().isEmpty() &&
               loanAmount != null && loanAmount.compareTo(BigDecimal.ZERO) > 0 &&
               loanTermMonths != null && loanTermMonths > 0 &&
               interestRate != null && interestRate.compareTo(BigDecimal.ZERO) >= 0;
    }
}

