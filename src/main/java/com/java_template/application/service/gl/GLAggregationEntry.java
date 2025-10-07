package com.java_template.application.service.gl;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Represents an aggregated journal entry line in a GL monthly report.
 * 
 * <p>Each entry represents the sum of all journal entries that share the same
 * aggregation key (asOfDate, account, direction, currency, priorPeriodFlag).</p>
 * 
 * <p>Example: All DR INTEREST_RECEIVABLE entries in USD for 2025-08-15 that are
 * not prior period adjustments would be summed into a single GLAggregationEntry.</p>
 */
@Data
public class GLAggregationEntry {
    
    /**
     * The composite key identifying this aggregation group
     */
    @JsonProperty("key")
    private final GLAggregationKey key;
    
    /**
     * Total amount summed across all journal entries in this group
     */
    @JsonProperty("totalAmount")
    private final BigDecimal totalAmount;
    
    /**
     * Number of individual journal entries aggregated into this entry
     */
    @JsonProperty("entryCount")
    private final int entryCount;

    /**
     * Constructor for creating an aggregation entry.
     * 
     * @param key The aggregation key identifying this group
     * @param totalAmount Sum of all amounts in this group
     * @param entryCount Number of entries aggregated
     */
    public GLAggregationEntry(GLAggregationKey key, BigDecimal totalAmount, int entryCount) {
        this.key = key;
        this.totalAmount = totalAmount;
        this.entryCount = entryCount;
    }

    @Override
    public String toString() {
        return String.format("GLAggregationEntry{key=%s, totalAmount=%s, entryCount=%d}",
                key, totalAmount, entryCount);
    }
}

