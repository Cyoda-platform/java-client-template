package com.java_template.application.controller;

import com.java_template.application.dto.dashboard.DashboardSummaryDTO;
import com.java_template.application.service.dashboard.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for dashboard data endpoints.
 *
 * <p>Provides HTTP access to dashboard aggregation services. The dashboard data
 * includes portfolio metrics, loan statistics, and time-based trends.</p>
 *
 * <p><strong>Endpoints:</strong></p>
 * <ul>
 *   <li>GET /ui/dashboard/summary - Retrieves aggregated dashboard data</li>
 *   <li>POST /ui/dashboard/cache/invalidate - Manually invalidates cache</li>
 * </ul>
 *
 * <p><strong>Caching:</strong></p>
 * <p>Dashboard data is cached for 5 minutes. Use the cache invalidation endpoint
 * to force immediate refresh after bulk data changes.</p>
 *
 * <p><strong>CORS:</strong></p>
 * <p>CORS is enabled for all origins to support frontend access.</p>
 */
@RestController
@RequestMapping("/ui/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {
    
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);
    
    private final DashboardService dashboardService;
    
    /**
     * Constructor with dependency injection.
     * 
     * @param dashboardService Service for dashboard data aggregation
     */
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }
    
    /**
     * Retrieves aggregated dashboard summary data.
     *
     * <p><strong>HTTP Method:</strong> GET</p>
     * <p><strong>Path:</strong> /ui/dashboard/summary</p>
     *
     * <p><strong>Response Format:</strong></p>
     * <p>Returns comprehensive dashboard metrics including:</p>
     * <ul>
     *   <li>Total portfolio value</li>
     *   <li>Active loans count</li>
     *   <li>Outstanding principal</li>
     *   <li>Active borrowers count</li>
     *   <li>Status distribution</li>
     *   <li>Portfolio trend (last 12 months)</li>
     *   <li>APR distribution</li>
     *   <li>Monthly payments (last 6 months)</li>
     * </ul>
     *
     * <p><strong>Caching:</strong></p>
     * <p>Data is cached for 5 minutes (300,000 milliseconds). Subsequent requests
     * within the cache TTL return cached data without querying the database.</p>
     *
     * <p><strong>HTTP Status Codes:</strong></p>
     * <ul>
     *   <li>200 OK - Successfully retrieved dashboard summary</li>
     *   <li>500 Internal Server Error - Failed to retrieve or aggregate data</li>
     *   <li>503 Service Unavailable - Dashboard service is unavailable</li>
     * </ul>
     *
     * <p><strong>Authentication:</strong></p>
     * <p>No authentication required (per SecurityConfig).</p>
     *
     * @return ResponseEntity with DashboardSummaryDTO containing all aggregated metrics
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getDashboardSummary() {
        logger.info("GET /ui/dashboard/summary - Retrieving dashboard summary");

        // Defensive check for service availability
        if (dashboardService == null) {
            logger.error("Dashboard service is unavailable (null)");
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Dashboard service is currently unavailable"
            );
            return ResponseEntity.of(problemDetail).build();
        }

        try {
            DashboardSummaryDTO summary = dashboardService.getDashboardSummary();

            // Log success with summary statistics
            logger.info("Successfully retrieved dashboard summary - Portfolio: {}, Active Loans: {}, Active Borrowers: {}",
                summary.getTotalPortfolioValue(),
                summary.getActiveLoansCount(),
                summary.getActiveBorrowersCount());

            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            logger.error("Failed to retrieve dashboard summary: {}", e.getMessage(), e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                String.format("Failed to retrieve dashboard summary: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
    
    /**
     * Manually invalidates the dashboard data cache.
     *
     * <p>Forces the next call to GET /ui/dashboard/summary to refresh data
     * from the database. Useful when immediate data refresh is required after
     * significant data changes such as:</p>
     * <ul>
     *   <li>Bulk loan imports</li>
     *   <li>Batch payment processing</li>
     *   <li>Mass loan state updates</li>
     * </ul>
     *
     * @return ResponseEntity with 204 No Content status
     */
    @PostMapping("/cache/invalidate")
    public ResponseEntity<Void> invalidateCache() {
        logger.info("POST /ui/dashboard/cache/invalidate - Invalidating dashboard cache");

        dashboardService.invalidateCache();
        logger.info("Dashboard cache successfully invalidated");

        return ResponseEntity.noContent().build();
    }
}

