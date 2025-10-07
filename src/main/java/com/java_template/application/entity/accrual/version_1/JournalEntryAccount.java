package com.java_template.application.entity.accrual.version_1;

/**
 * General ledger accounts used in accrual journal entries.
 * 
 * For daily interest accruals, entries are always:
 * - DR INTEREST_RECEIVABLE (asset account)
 * - CR INTEREST_INCOME (revenue account)
 */
public enum JournalEntryAccount {
    /**
     * Asset account representing interest earned but not yet received
     */
    INTEREST_RECEIVABLE,
    
    /**
     * Revenue account representing interest income earned
     */
    INTEREST_INCOME
}

