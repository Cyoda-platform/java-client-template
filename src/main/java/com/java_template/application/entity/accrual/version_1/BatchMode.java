package com.java_template.application.entity.accrual.version_1;

/**
 * Enum representing the mode of an EOD accrual batch run.
 * 
 * <p>TODAY mode indicates a normal end-of-day run for the current business date.
 * BACKDATED mode indicates a historical correction run for a past business date,
 * which requires a reason code and may trigger cascade recalculations.</p>
 */
public enum BatchMode {
    /**
     * Normal end-of-day run for the current business date
     */
    TODAY,
    
    /**
     * Back-dated correction run for a historical business date
     * Requires a reasonCode and may trigger cascade recalculations
     */
    BACKDATED
}

