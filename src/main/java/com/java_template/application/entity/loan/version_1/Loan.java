package com.java_template.application.entity.loan.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ABOUTME: This entity represents a funded commercial loan under servicing,
 * acting as the aggregate root for most financial activities in the LMS.
 */
@Data
public class Loan implements CyodaEntity {
    public static final String ENTITY_NAME = Loan.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String loanId;

    // Required core business fields
    private String agreementId;
    private String partyId; // Reference to borrower Party
    private BigDecimal principalAmount;
    private BigDecimal apr; // Annual Percentage Rate
    private Integer termMonths; // 12, 24, or 36 months
    private LocalDate fundingDate;
    private LocalDate maturityDate;

    // Financial balances (managed by system)
    private BigDecimal outstandingPrincipal;
    private BigDecimal accruedInterest;

    // Optional fields for additional business data
    private String purpose; // e.g., "General corporate purposes"
    private String governingLaw; // e.g., "England and Wales"
    private String dayCountBasis; // e.g., "ACT/365", "ACT/360", "30/360"
    private String currency; // e.g., "GBP", "USD"
    private List<LoanParty> parties; // All parties involved in the loan
    private List<LoanFacility> facilities;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields
        return loanId != null && !loanId.trim().isEmpty() &&
               agreementId != null && !agreementId.trim().isEmpty() &&
               partyId != null && !partyId.trim().isEmpty() &&
               principalAmount != null && principalAmount.compareTo(BigDecimal.ZERO) > 0 &&
               apr != null && apr.compareTo(BigDecimal.ZERO) > 0 &&
               termMonths != null && (termMonths == 12 || termMonths == 24 || termMonths == 36) &&
               fundingDate != null &&
               maturityDate != null;
    }

    /**
     * Nested class for loan parties (borrowers, lenders, agents)
     */
    @Data
    public static class LoanParty {
        private String partyId;
        private String name;
        private String lei;
        private String role; // "Borrower", "Lender", "Agent", "Security Trustee"
        private String jurisdiction;
        private BigDecimal commitmentAmount;
        private String commitmentCurrency;
    }

    /**
     * Nested class for loan facilities
     */
    @Data
    public static class LoanFacility {
        private String facilityId;
        private String type; // "Revolver", "Term Loan"
        private String currency;
        private BigDecimal limit;
        private LoanAvailability availability;
        private List<LoanTranche> tranches;
        private List<LoanDrawdown> drawdowns;
        private List<LoanRepayment> repayments;
        private LoanPrepayment prepayment;
    }

    /**
     * Nested class for facility availability
     */
    @Data
    public static class LoanAvailability {
        private LocalDate startDate;
        private LocalDate endDate;
        private List<String> conditionsPrecedent;
    }

    /**
     * Nested class for loan tranches
     */
    @Data
    public static class LoanTranche {
        private String trancheId;
        private BigDecimal limit;
        private String purpose;
        private LoanInterest interest;
        private List<LoanFee> fees;
        private LoanAmortization amortization;
        private List<LoanCovenant> covenants;
        private List<LoanCollateral> collateral;
    }

    /**
     * Nested class for interest configuration
     */
    @Data
    public static class LoanInterest {
        private String index; // "SONIA", "LIBOR"
        private String tenor; // "1M", "3M"
        private Integer spreadBps; // Spread in basis points
        private BigDecimal floorRate;
        private String dayCount; // "ACT/365F", "ACT/360"
        private LoanRateReset rateReset;
        private String compounding; // "Simple", "Compound"
    }

    /**
     * Nested class for rate reset configuration
     */
    @Data
    public static class LoanRateReset {
        private String frequency; // "Monthly", "Quarterly"
        private String businessDayConvention; // "ModifiedFollowing"
    }

    /**
     * Nested class for fees
     */
    @Data
    public static class LoanFee {
        private String feeId;
        private String type; // "Commitment", "Arrangement"
        private String basis; // "Unused", "Outstanding"
        private Integer rateBps;
        private BigDecimal amount;
        private String accrualDayCount;
        private String payFrequency;
        private String payOn; // "Signing", "Quarterly"
    }

    /**
     * Nested class for amortization
     */
    @Data
    public static class LoanAmortization {
        private String type; // "Bullet", "Amortizing"
        private List<String> schedule; // Empty for bullet loans
    }

    /**
     * Nested class for covenants
     */
    @Data
    public static class LoanCovenant {
        private String covenantId;
        private String category; // "Financial", "Information"
        private String name; // "Net Leverage"
        private String definition; // "NetDebt/EBITDA"
        private String thresholdOperator; // "<=", ">="
        private BigDecimal thresholdValue;
        private String testFrequency; // "Quarterly", "Monthly"
        private LoanCureRights cureRights;
    }

    /**
     * Nested class for cure rights
     */
    @Data
    public static class LoanCureRights {
        private Boolean allowed;
        private Integer periodDays;
    }

    /**
     * Nested class for collateral
     */
    @Data
    public static class LoanCollateral {
        private String collateralId;
        private String type; // "Debenture", "Pledge"
        private String jurisdiction;
        private String description;
    }

    /**
     * Nested class for drawdowns
     */
    @Data
    public static class LoanDrawdown {
        private String drawId;
        private String trancheId;
        private LocalDate requestDate;
        private LocalDate valueDate;
        private BigDecimal amount;
        private String purpose;
        private LoanFx fx;
    }

    /**
     * Nested class for FX information
     */
    @Data
    public static class LoanFx {
        private String tradeCcy;
        private String settleCcy;
        private BigDecimal rate;
    }

    /**
     * Nested class for repayments
     */
    @Data
    public static class LoanRepayment {
        private String repaymentId;
        private String type; // "Scheduled", "Prepayment"
        private LocalDate dueDate;
        private BigDecimal amount;
        private LoanAllocation allocation;
    }

    /**
     * Nested class for payment allocation
     */
    @Data
    public static class LoanAllocation {
        private BigDecimal principal;
        private BigDecimal interest;
        private BigDecimal fees;
    }

    /**
     * Nested class for prepayment terms
     */
    @Data
    public static class LoanPrepayment {
        private LoanVoluntary voluntary;
        private LoanMandatory mandatory;
    }

    /**
     * Nested class for voluntary prepayment
     */
    @Data
    public static class LoanVoluntary {
        private Integer noticeDays;
        private Boolean breakCostsApplicable;
        private BigDecimal minimumAmount;
        private BigDecimal multipleAmount;
    }

    /**
     * Nested class for mandatory prepayment
     */
    @Data
    public static class LoanMandatory {
        private List<String> events; // "Asset Sale", "Insurance Proceeds"
        private BigDecimal threshold;
        private String application; // "Pro Rata", "Waterfall"
    }
}
