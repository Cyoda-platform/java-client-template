package com.java_template.application.entity.loan.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: This file contains the Loan entity representing fixed-term commercial loans
 * with lifecycle management from draft through funding to settlement or closure.
 */
@Data
@NoArgsConstructor
public class Loan implements CyodaEntity {
    public static final String ENTITY_NAME = Loan.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    @NonNull
    private String loanId;
    
    // Party reference
    private String partyId;
    
    // Core loan terms
    private BigDecimal principalAmount;
    private BigDecimal apr; // Annual Percentage Rate
    private Integer termMonths; // 12, 24, or 36 months
    private String dayCountBasis; // ACT/365F, ACT/360, ACT/365L
    private Integer repaymentDay; // Day of month for repayments
    
    // Funding information
    private LocalDate fundedDate;
    private BigDecimal fundedAmount;
    
    // Current balances
    private BigDecimal outstandingPrincipal;
    private BigDecimal accruedInterest;
    private BigDecimal totalInterestReceivable;
    
    // Schedule and dates
    private LocalDate maturityDate;
    private LocalDate nextDueDate;
    private List<LoanScheduleItem> referenceSchedule;
    
    // Approval information
    private LoanApproval approval;
    
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
        return loanId != null && !loanId.trim().isEmpty() &&
               partyId != null && !partyId.trim().isEmpty() &&
               principalAmount != null && principalAmount.compareTo(BigDecimal.ZERO) > 0 &&
               apr != null && apr.compareTo(BigDecimal.ZERO) > 0 &&
               termMonths != null && (termMonths == 12 || termMonths == 24 || termMonths == 36) &&
               dayCountBasis != null && !dayCountBasis.trim().isEmpty() &&
               repaymentDay != null && repaymentDay >= 1 && repaymentDay <= 31;
    }

    /**
     * Nested class for loan schedule items
     */
    @Data
    public static class LoanScheduleItem {
        private LocalDate dueDate;
        private BigDecimal principalAmount;
        private BigDecimal interestAmount;
        private BigDecimal totalAmount;
        private BigDecimal remainingBalance;
    }

    /**
     * Nested class for approval information
     */
    @Data
    public static class LoanApproval {
        private String approvedBy;
        private LocalDateTime approvedAt;
        private String approverRole;
        private String approvalComments;
        private String checkedBy;
        private LocalDateTime checkedAt;
        private String checkerRole;
    }
}
