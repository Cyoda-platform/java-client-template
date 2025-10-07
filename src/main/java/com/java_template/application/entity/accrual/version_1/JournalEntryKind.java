package com.java_template.application.entity.accrual.version_1;

/**
 * Kind of journal entry, used to track the purpose and lineage of entries.
 * 
 * This is critical for rebook scenarios where prior entries must be reversed
 * and replaced with corrected amounts.
 */
public enum JournalEntryKind {
    /**
     * Original entry created during normal accrual posting
     */
    ORIGINAL,
    
    /**
     * Reversal entry that offsets a prior entry (must reference adjustsEntryId)
     * Used when correcting or superseding a previously posted accrual
     */
    REVERSAL,
    
    /**
     * Replacement entry with corrected amounts for the same asOfDate
     * Created alongside REVERSAL entries during rebook operations
     */
    REPLACEMENT
}

