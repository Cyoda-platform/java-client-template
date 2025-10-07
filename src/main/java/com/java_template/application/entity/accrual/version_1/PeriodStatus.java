package com.java_template.application.entity.accrual.version_1;

/**
 * Enum representing the GL period status for the batch's asOfDate.
 * 
 * <p>OPEN indicates the GL period is still open for normal postings.
 * CLOSED indicates the GL period has been closed, requiring prior-period adjustment handling.</p>
 */
public enum PeriodStatus {
    /**
     * GL period is open for normal postings
     */
    OPEN,
    
    /**
     * GL period is closed; postings will be flagged as prior-period adjustments
     */
    CLOSED
}

