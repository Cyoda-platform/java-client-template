package com.java_template.application.service.dashboard;

import com.java_template.application.dto.dashboard.DashboardSummaryDTO;

/**
 * Service interface for dashboard data aggregation.
 * 
 * <p>Provides aggregated metrics and trends for the loan management dashboard,
 * including portfolio values, loan counts, borrower statistics, and time-based trends.</p>
 * 
 * <p><strong>Caching Behavior:</strong></p>
 * <ul>
 *   <li>Dashboard data is cached for 5 minutes (300,000 milliseconds)</li>
 *   <li>Subsequent requests within the cache TTL return cached data</li>
 *   <li>Cache automatically expires after TTL and refreshes on next request</li>
 *   <li>Manual cache invalidation is available via {@link #invalidateCache()}</li>
 * </ul>
 * 
 * <p><strong>Performance Considerations:</strong></p>
 * <ul>
 *   <li>Caching reduces database load from 120 queries/hour (30-second refresh) to 12 queries/hour (5-minute TTL)</li>
 *   <li>In-memory aggregation is acceptable for expected data volumes (hundreds to thousands of loans)</li>
 *   <li>Consider future optimization if data volumes exceed 10,000+ entities</li>
 * </ul>
 */
public interface DashboardService {
    
    /**
     * Retrieves aggregated dashboard summary data.
     * 
     * <p>This method aggregates data from multiple entity types (Loan, Payment, Party)
     * and calculates various metrics including:</p>
     * <ul>
     *   <li>Total portfolio value (sum of all loan principal amounts)</li>
     *   <li>Active loans count (loans in "active" or "funded" states)</li>
     *   <li>Outstanding principal (sum of outstanding principal for active/funded loans)</li>
     *   <li>Active borrowers count (distinct borrowers with active/funded loans)</li>
     *   <li>Status distribution (count of loans by workflow state)</li>
     *   <li>Portfolio trend (monthly portfolio values for last 12 months)</li>
     *   <li>APR distribution (array of APR values for all loans)</li>
     *   <li>Monthly payments (sum of payments by month for last 6 months)</li>
     * </ul>
     * 
     * <p>The result is cached for 5 minutes. Subsequent calls within the cache TTL
     * return the cached data without querying the database.</p>
     * 
     * @return DashboardSummaryDTO containing all aggregated metrics
     * @throws RuntimeException if data retrieval or aggregation fails
     */
    DashboardSummaryDTO getDashboardSummary();
    
    /**
     * Manually invalidates the dashboard data cache.
     * 
     * <p>Forces the next call to {@link #getDashboardSummary()} to refresh data
     * from the database. Useful when immediate data refresh is required after
     * significant data changes (e.g., bulk loan imports, batch updates).</p>
     */
    void invalidateCache();
}

