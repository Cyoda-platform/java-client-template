package com.java_template.application.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for portfolio trend data over time.
 * 
 * <p>Contains parallel arrays of month labels and their corresponding portfolio values.
 * Used for rendering portfolio trend charts showing growth over the last 12 months.</p>
 * 
 * <p>Example JSON structure:</p>
 * <pre>
 * {
 *   "months": ["2024-11", "2024-12", "2025-01", "2025-02"],
 *   "values": [1500000.00, 1750000.00, 2000000.00, 2250000.00]
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioTrendDTO {
    
    /**
     * Array of month labels in YYYY-MM format.
     */
    private List<String> months;
    
    /**
     * Array of portfolio values corresponding to each month.
     * The value at index i corresponds to the month at index i.
     */
    private List<BigDecimal> values;
}

