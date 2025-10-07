package com.java_template.application.entity.accrual.version_1;

/**
 * Direction of a journal entry (debit or credit).
 * 
 * In double-entry bookkeeping:
 * - DR (Debit) increases asset and expense accounts
 * - CR (Credit) increases liability, equity, and revenue accounts
 */
public enum JournalEntryDirection {
    /**
     * Debit entry - increases assets and expenses
     */
    DR,
    
    /**
     * Credit entry - increases liabilities, equity, and revenue
     */
    CR
}

