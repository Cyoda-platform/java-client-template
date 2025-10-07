package com.java_template.application.entity.accrual.version_1;

/**
 * Enum representing the lifecycle states of an EOD accrual batch.
 * 
 * <p>The batch progresses through these states as it orchestrates the daily
 * accrual run, from initial request through snapshot, generation, posting,
 * optional cascade recalculation, reconciliation, and final completion.</p>
 */
public enum EODAccrualBatchState {
    /**
     * Initial state when batch is created
     */
    REQUESTED,
    
    /**
     * Batch has been validated and is ready to proceed
     */
    VALIDATED,
    
    /**
     * Principal and APR snapshots have been captured for the asOfDate
     */
    SNAPSHOT_TAKEN,
    
    /**
     * Accruals are being generated for eligible loans
     */
    GENERATING,
    
    /**
     * All accruals have been posted successfully
     */
    POSTING_COMPLETE,
    
    /**
     * Cascade recalculation is in progress (for backdated runs)
     */
    CASCADING,
    
    /**
     * Reconciliation and report generation in progress
     */
    RECONCILING,
    
    /**
     * Batch has completed successfully
     */
    COMPLETED,
    
    /**
     * Batch has failed
     */
    FAILED,
    
    /**
     * Batch has been canceled
     */
    CANCELED
}

