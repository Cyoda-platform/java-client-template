package com.java_template.application.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for dashboard summary data.
 * 
 * <p>Contains all aggregated metrics and trends for the loan management dashboard.
 * This data is cached for 5 minutes to reduce database load.</p>
 * 
 * <p>Example JSON structure:</p>
 * <pre>
 * {
 *   "totalPortfolioValue": 5000000.00,
 *   "activeLoansCount": 45,
 *   "outstandingPrincipal": 4250000.00,
 *   "activeBorrowersCount": 38,
 *   "statusDistribution": {
 *     "labels": ["active", "funded", "matured"],
 *     "values": [45, 12, 8]
 *   },
 *   "portfolioTrend": {
 *     "months": ["2024-11", "2024-12", "2025-01"],
 *     "values": [1500000.00, 1750000.00, 2000000.00]
 *   },
 *   "aprDistribution": [5.5, 6.0, 6.5, 7.0],
 *   "monthlyPayments": {
 *     "months": ["2024-11", "2024-12", "2025-01"],
 *     "amounts": [125000.00, 135000.00, 142000.00]
 *   }
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDTO {
    
    /**
     * Total portfolio value (sum of all loan principal amounts).
     */
    private BigDecimal totalPortfolioValue;
    
    /**
     * Count of active loans (loans in "active" or "funded" states).
     */
    private Integer activeLoansCount;
    
    /**
     * Total outstanding principal across all active/funded loans.
     */
    private BigDecimal outstandingPrincipal;
    
    /**
     * Count of distinct borrowers with active/funded loans.
     */
    private Integer activeBorrowersCount;
    
    /**
     * Distribution of loans by workflow state.
     */
    private StatusDistributionDTO statusDistribution;
    
    /**
     * Portfolio value trend over the last 12 months.
     */
    private PortfolioTrendDTO portfolioTrend;
    
    /**
     * Array of APR values for all loans (used for distribution charts).
     */
    private List<BigDecimal> aprDistribution;
    
    /**
     * Monthly payment amounts over the last 6 months.
     */
    private MonthlyPaymentsDTO monthlyPayments;
}

