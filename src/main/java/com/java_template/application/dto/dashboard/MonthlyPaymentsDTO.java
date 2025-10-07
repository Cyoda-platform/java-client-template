package com.java_template.application.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for monthly payment data over time.
 * 
 * <p>Contains parallel arrays of month labels and their corresponding payment amounts.
 * Used for rendering monthly payment trend charts showing payment activity over the last 6 months.</p>
 * 
 * <p>Example JSON structure:</p>
 * <pre>
 * {
 *   "months": ["2024-11", "2024-12", "2025-01"],
 *   "amounts": [125000.00, 135000.00, 142000.00]
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyPaymentsDTO {
    
    /**
     * Array of month labels in YYYY-MM format.
     */
    private List<String> months;
    
    /**
     * Array of payment amounts corresponding to each month.
     * The amount at index i corresponds to the month at index i.
     */
    private List<BigDecimal> amounts;
}

