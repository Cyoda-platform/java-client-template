package com.java_template.application.entity.accrual.version_1;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Embedded journal entry within an Accrual entity.
 * 
 * INHERITANCE CONTRACT:
 * Journal entries do NOT contain the following fields, which are inherited from the parent Accrual:
 * - asOfDate (effective date) - inherited from Accrual.asOfDate
 * - currency - inherited from Accrual.currency
 * - loanId - inherited from Accrual.loanId
 * - postingTimestamp - inherited from Accrual.postingTimestamp
 * - priorPeriodFlag - inherited from Accrual.priorPeriodFlag
 * - runId - inherited from Accrual.runId
 * 
 * These values are resolved from the parent Accrual at read/export time.
 * This design avoids data duplication and ensures consistency.
 * 
 * For normal accruals, two entries are created:
 * - DR INTEREST_RECEIVABLE for interestAmount (ORIGINAL)
 * - CR INTEREST_INCOME for interestAmount (ORIGINAL)
 * 
 * For rebook scenarios, additional entries are created:
 * - REVERSAL entries that offset prior entries (must set adjustsEntryId)
 * - REPLACEMENT entries with corrected amounts
 */
@Data
public class JournalEntry {
    /**
     * Unique identifier for this journal entry
     */
    @JsonProperty("entryId")
    private String entryId;
    
    /**
     * General ledger account for this entry
     */
    @JsonProperty("account")
    private JournalEntryAccount account;
    
    /**
     * Direction of the entry (DR or CR)
     */
    @JsonProperty("direction")
    private JournalEntryDirection direction;
    
    /**
     * Amount of the entry in the currency inherited from parent Accrual
     */
    @JsonProperty("amount")
    private BigDecimal amount;
    
    /**
     * Kind of entry (ORIGINAL, REVERSAL, or REPLACEMENT)
     */
    @JsonProperty("kind")
    private JournalEntryKind kind;
    
    /**
     * Reference to the entry being adjusted (required for REVERSAL entries)
     * Points to the entryId of the original entry being reversed
     */
    @JsonProperty("adjustsEntryId")
    private String adjustsEntryId;
    
    /**
     * Optional memo/description for the entry
     */
    @JsonProperty("memo")
    private String memo;
    
    /**
     * Validates this journal entry.
     * 
     * Business rules:
     * - REVERSAL entries must have adjustsEntryId set
     * - Amount must be positive
     * - Required fields must be present
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        // Check required fields
        if (entryId == null || account == null || direction == null || 
            amount == null || kind == null) {
            return false;
        }
        
        // Amount must be positive
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        // REVERSAL entries must reference the entry being reversed
        if (kind == JournalEntryKind.REVERSAL && adjustsEntryId == null) {
            return false;
        }
        
        return true;
    }
}

