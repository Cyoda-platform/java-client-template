package com.java_template.application.entity.accrual.version_1;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Metrics tracking the progress and results of an EOD accrual batch run.
 * 
 * <p>These metrics are updated as the batch progresses through its lifecycle,
 * providing visibility into the number of loans processed, accruals created,
 * and the financial totals of debits and credits posted.</p>
 */
@Data
public class BatchMetrics {
    /**
     * Number of loans eligible for accrual processing in this batch
     */
    @JsonProperty("eligibleLoans")
    private int eligibleLoans;
    
    /**
     * Number of loans that have been processed (successfully or with errors)
     */
    @JsonProperty("processedLoans")
    private int processedLoans;
    
    /**
     * Number of accrual entities created by this batch
     */
    @JsonProperty("accrualsCreated")
    private int accrualsCreated;
    
    /**
     * Number of journal entry postings made by this batch
     */
    @JsonProperty("postings")
    private int postings;
    
    /**
     * Total amount debited across all journal entries
     */
    @JsonProperty("debited")
    private BigDecimal debited;
    
    /**
     * Total amount credited across all journal entries
     */
    @JsonProperty("credited")
    private BigDecimal credited;
    
    /**
     * Number of imbalances detected (where debits != credits)
     */
    @JsonProperty("imbalances")
    private int imbalances;
}

