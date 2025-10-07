package com.java_template.application.entity.accrual.version_1;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Error information for failed accruals.
 * 
 * When an accrual transitions to FAILED state, this object captures
 * the error details for troubleshooting and reporting.
 */
@Data
public class AccrualError {
    /**
     * Error code identifying the type of failure
     * Examples: "INVALID_PRINCIPAL", "MISSING_APR", "CALCULATION_ERROR"
     */
    @JsonProperty("code")
    private String code;
    
    /**
     * Human-readable error message describing the failure
     */
    @JsonProperty("message")
    private String message;
}

