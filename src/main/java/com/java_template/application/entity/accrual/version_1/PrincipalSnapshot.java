package com.java_template.application.entity.accrual.version_1;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Snapshot of the loan's principal balance used for accrual calculation.
 * 
 * The principal amount is captured at a specific point in time relative to
 * the asOfDate to ensure consistent accrual calculations.
 */
@Data
public class PrincipalSnapshot {
    /**
     * The principal amount at the time of snapshot
     */
    @JsonProperty("amount")
    private BigDecimal amount;
    
    /**
     * Whether this principal amount is effective at the start of the day (true)
     * or end of day (false) for the asOfDate.
     * 
     * This is important for determining which principal balance to use when
     * payments or disbursements occur on the asOfDate.
     */
    @JsonProperty("effectiveAtStartOfDay")
    private Boolean effectiveAtStartOfDay;
}

