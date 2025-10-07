package com.java_template.application.entity.accrual.version_1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;

/**
 * Accrual entity representing daily interest computation for a loan as-of a specific date.
 * 
 * This is the single source of truth for accrual data, with embedded journal entries
 * that follow an inheritance contract (see JournalEntry class for details).
 * 
 * BUSINESS INVARIANTS:
 * 1. For POSTED accruals: sum of DR entries must equal sum of CR entries
 * 2. Idempotency key: (loanId, asOfDate, "DAILY_INTEREST")
 * 3. All journal entries inherit: asOfDate, currency, loanId, postingTimestamp, priorPeriodFlag, runId
 * 4. Effective date for all journal entries equals asOfDate
 * 
 * LIFECYCLE:
 * NEW -> ELIGIBLE -> CALCULATED -> POSTED -> [SUPERSEDED]
 * Terminal states: SUPERSEDED, FAILED, CANCELED
 */
@Data
public class Accrual implements CyodaEntity {
    public static final String ENTITY_NAME = Accrual.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    /**
     * Unique identifier for this accrual
     */
    @JsonProperty("accrualId")
    private String accrualId;
    
    /**
     * Reference to the loan for which this accrual is calculated
     */
    @JsonProperty("loanId")
    private String loanId;
    
    /**
     * Business date for which this accrual is calculated
     * Also serves as the effective date for all journal entries
     */
    @JsonProperty("asOfDate")
    private LocalDate asOfDate;
    
    /**
     * Currency for all amounts in this accrual (ISO-4217 code)
     */
    @JsonProperty("currency")
    private String currency;
    
    /**
     * Reference to the APR (Annual Percentage Rate) used for calculation
     */
    @JsonProperty("aprId")
    private String aprId;
    
    /**
     * Day count convention used for interest calculation
     */
    @JsonProperty("dayCountConvention")
    private DayCountConvention dayCountConvention;
    
    /**
     * Calculated day count fraction (e.g., 1/360 for ACT_360)
     */
    @JsonProperty("dayCountFraction")
    private BigDecimal dayCountFraction;
    
    /**
     * Snapshot of the principal balance used for accrual calculation
     */
    @JsonProperty("principalSnapshot")
    private PrincipalSnapshot principalSnapshot;
    
    /**
     * Calculated interest amount for the day
     * Formula: principalSnapshot.amount × APR × dayCountFraction
     */
    @JsonProperty("interestAmount")
    private BigDecimal interestAmount;
    
    /**
     * Timestamp when the accrual was posted to the subledger
     */
    @JsonProperty("postingTimestamp")
    private OffsetDateTime postingTimestamp;
    
    /**
     * Flag indicating if this is a prior-period adjustment (back-dated into closed GL month)
     */
    @JsonProperty("priorPeriodFlag")
    private Boolean priorPeriodFlag;
    
    /**
     * Reference to the batch run that produced this accrual (optional)
     */
    @JsonProperty("runId")
    private String runId;
    
    /**
     * Version number for this accrual (for optimistic locking)
     */
    @JsonProperty("version")
    private Integer version;
    
    /**
     * Reference to the accrual being superseded (for rebook scenarios)
     * When set, this accrual replaces a prior accrual for the same asOfDate
     */
    @JsonProperty("supersedesAccrualId")
    private String supersedesAccrualId;
    
    /**
     * Current lifecycle state of the accrual
     */
    @JsonProperty("state")
    private AccrualState state;
    
    /**
     * Embedded journal entries for this accrual
     * These entries inherit asOfDate, currency, loanId, postingTimestamp, priorPeriodFlag, and runId
     */
    @JsonProperty("journalEntries")
    private List<JournalEntry> journalEntries;
    
    /**
     * Error information if the accrual failed
     */
    @JsonProperty("error")
    private AccrualError error;

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
        if (loanId == null || loanId.trim().isEmpty()) {
            return false;
        }
        
        if (asOfDate == null) {
            return false;
        }
        
        if (currency == null || currency.trim().isEmpty()) {
            return false;
        }
        
        // Validate currency is a valid ISO-4217 code
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            return false;
        }
        
        // For POSTED accruals, validate debit/credit balance
        if (state == AccrualState.POSTED) {
            if (!isBalanced()) {
                return false;
            }
        }
        
        // Validate all journal entries if present
        if (journalEntries != null) {
            for (JournalEntry entry : journalEntries) {
                if (!entry.isValid()) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Checks if the journal entries are balanced (sum of debits equals sum of credits).
     * This is a required invariant for POSTED accruals.
     * 
     * @return true if balanced or no entries, false otherwise
     */
    private boolean isBalanced() {
        if (journalEntries == null || journalEntries.isEmpty()) {
            return true;
        }
        
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        
        for (JournalEntry entry : journalEntries) {
            if (entry.getDirection() == JournalEntryDirection.DR) {
                totalDebits = totalDebits.add(entry.getAmount());
            } else if (entry.getDirection() == JournalEntryDirection.CR) {
                totalCredits = totalCredits.add(entry.getAmount());
            }
        }
        
        return totalDebits.compareTo(totalCredits) == 0;
    }
}

