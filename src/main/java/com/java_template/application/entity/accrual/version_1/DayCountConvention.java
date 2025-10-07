package com.java_template.application.entity.accrual.version_1;

/**
 * Day count conventions used for interest accrual calculations.
 * 
 * These conventions determine how to calculate the day count fraction
 * for interest accrual periods.
 */
public enum DayCountConvention {
    /**
     * Actual/360 - Actual days in period divided by 360
     * Common in money market instruments and commercial loans
     */
    ACT_360,
    
    /**
     * Actual/365 - Actual days in period divided by 365
     * Common in UK and Commonwealth markets
     */
    ACT_365,
    
    /**
     * 30/360 - Assumes 30 days per month and 360 days per year
     * Common in corporate bonds
     */
    THIRTY_360
}

