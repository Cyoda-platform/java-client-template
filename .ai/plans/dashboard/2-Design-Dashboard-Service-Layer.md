# Actionable Step: Design Dashboard Service Layer

**Objective:** Design the service layer architecture for dashboard data aggregation, including DTOs, service interfaces, and caching strategy.

**Prerequisites:** 
- Actionable Step 1 (Analyze Existing Patterns and Data Model) must be completed

**Action Items:**
1. Design the DashboardSummaryDTO class structure to match the required JSON response format:
   - totalPortfolioValue (BigDecimal)
   - activeLoansCount (Integer)
   - outstandingPrincipal (BigDecimal)
   - activeBorrowersCount (Integer)
   - statusDistribution (nested object with labels and values arrays)
   - portfolioTrend (nested object with months and values arrays)
   - aprDistribution (List<BigDecimal>)
   - monthlyPayments (nested object with months and amounts arrays)
2. Design nested DTO classes for complex structures:
   - StatusDistributionDTO (List<String> labels, List<Integer> values)
   - PortfolioTrendDTO (List<String> months, List<BigDecimal> values)
   - MonthlyPaymentsDTO (List<String> months, List<BigDecimal> amounts)
3. Design the DashboardService interface with method signature:
   - `DashboardSummaryDTO getDashboardSummary()`
4. Design the caching strategy:
   - Use ConcurrentHashMap similar to Authentication.java pattern
   - Implement CachedDashboardSummary record with timestamp and data
   - Define TTL constant (5 minutes = 300,000 milliseconds)
   - Design cache invalidation logic based on timestamp comparison
5. Plan the data aggregation algorithm:
   - Step 1: Retrieve all loans using EntityService.findAll()
   - Step 2: Filter loans by metadata state for active/funded calculations
   - Step 3: Retrieve all payments using EntityService.findAll()
   - Step 4: Perform in-memory aggregations using Java Streams
   - Step 5: Group and calculate time-based metrics (monthly trends)
6. Design error handling strategy:
   - Handle EntityService exceptions gracefully
   - Return zeros/empty arrays for missing data scenarios
   - Log errors with sufficient context for debugging
7. Document performance considerations:
   - Caching reduces database load from 120 queries/hour (30-second refresh) to 12 queries/hour (5-minute TTL)
   - In-memory aggregation is acceptable for expected data volumes (hundreds to thousands of loans)
   - Consider future optimization if data volumes exceed 10,000+ entities
8. Create package structure plan:
   - `com.java_template.application.dto.dashboard` for DTOs
   - `com.java_template.application.service.dashboard` for service interface and implementation

**Acceptance Criteria:**
- All DTO classes are designed with proper field types matching the JSON response specification
- Service interface is clearly defined with method signatures
- Caching strategy is documented with TTL and invalidation logic
- Data aggregation algorithm is broken down into clear steps
- Error handling approach is defined
- Package structure follows existing codebase conventions
- Design document includes all necessary classes and their relationships

