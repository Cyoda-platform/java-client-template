package com.java_template.application.entity.accrual.version_1;

/**
 * Represents the lifecycle state of an Accrual entity.
 * 
 * State transitions follow the workflow defined in the Cyoda specification:
 * NEW -> ELIGIBLE -> CALCULATED -> POSTED -> SUPERSEDED
 * 
 * Terminal states: SUPERSEDED, FAILED, CANCELED
 */
public enum AccrualState {
    /**
     * Initial state when an accrual is first created
     */
    NEW,
    
    /**
     * Accrual has passed validation and is eligible for calculation
     */
    ELIGIBLE,
    
    /**
     * Interest amount has been calculated but not yet posted
     */
    CALCULATED,
    
    /**
     * Journal entries have been written and posted to the subledger
     */
    POSTED,
    
    /**
     * This accrual has been superseded by a newer accrual for the same asOfDate
     * (used in rebook scenarios)
     */
    SUPERSEDED,
    
    /**
     * Accrual processing failed due to an error
     */
    FAILED,
    
    /**
     * Accrual was manually canceled
     */
    CANCELED
}

