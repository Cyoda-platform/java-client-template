package com.java_template.application.service.gl;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Monthly GL aggregation report containing all journal entries for a specific month.
 * 
 * <p>This report aggregates all journal entries from accruals whose asOfDate falls
 * within the specified month, grouped by (asOfDate, account, direction, currency, priorPeriodFlag).</p>
 * 
 * <p>As specified in section 8 of the requirements, the report includes:</p>
 * <ul>
 *   <li>Aggregated journal entries grouped by key</li>
 *   <li>Total debits and credits for balance verification</li>
 *   <li>Separate section for prior period adjustments (PPAs)</li>
 *   <li>Batch file ID for traceability</li>
 *   <li>Checksum for data integrity verification</li>
 * </ul>
 */
@Data
public class GLMonthlyReport {
    
    /**
     * The month for which this report was generated
     */
    @JsonProperty("month")
    private final YearMonth month;
    
    /**
     * All aggregated journal entries (including both regular and PPA entries)
     */
    @JsonProperty("entries")
    private final List<GLAggregationEntry> entries;
    
    /**
     * Total of all debit amounts in the report
     */
    @JsonProperty("totalDebits")
    private final BigDecimal totalDebits;
    
    /**
     * Total of all credit amounts in the report
     */
    @JsonProperty("totalCredits")
    private final BigDecimal totalCredits;
    
    /**
     * Prior period adjustments separated for designated reporting section
     * These are entries where priorPeriodFlag=true
     */
    @JsonProperty("priorPeriodAdjustments")
    private final List<GLAggregationEntry> priorPeriodAdjustments;
    
    /**
     * Unique identifier for this batch file (UUID or timestamp-based)
     */
    @JsonProperty("batchFileId")
    private final String batchFileId;
    
    /**
     * Checksum of the report data for integrity verification
     */
    @JsonProperty("checksum")
    private final String checksum;

    /**
     * Constructor for creating a GL monthly report.
     * 
     * @param month The month being reported
     * @param entries All aggregated entries
     * @param totalDebits Sum of all DR amounts
     * @param totalCredits Sum of all CR amounts
     * @param priorPeriodAdjustments Entries with priorPeriodFlag=true
     * @param batchFileId Unique batch identifier
     * @param checksum Data integrity checksum
     */
    public GLMonthlyReport(YearMonth month, List<GLAggregationEntry> entries,
                          BigDecimal totalDebits, BigDecimal totalCredits,
                          List<GLAggregationEntry> priorPeriodAdjustments,
                          String batchFileId, String checksum) {
        this.month = month;
        this.entries = entries;
        this.totalDebits = totalDebits;
        this.totalCredits = totalCredits;
        this.priorPeriodAdjustments = priorPeriodAdjustments;
        this.batchFileId = batchFileId;
        this.checksum = checksum;
    }

    /**
     * Checks if the report is balanced (debits equal credits).
     * 
     * @return true if totalDebits equals totalCredits, false otherwise
     */
    public boolean isBalanced() {
        return totalDebits.compareTo(totalCredits) == 0;
    }

    /**
     * Gets the imbalance amount (difference between debits and credits).
     * 
     * @return Positive if debits exceed credits, negative if credits exceed debits, zero if balanced
     */
    public BigDecimal getImbalance() {
        return totalDebits.subtract(totalCredits);
    }

    @Override
    public String toString() {
        return String.format("GLMonthlyReport{month=%s, entries=%d, totalDebits=%s, totalCredits=%s, " +
                        "priorPeriodAdjustments=%d, batchFileId=%s, balanced=%s}",
                month, entries.size(), totalDebits, totalCredits,
                priorPeriodAdjustments.size(), batchFileId, isBalanced());
    }
}

