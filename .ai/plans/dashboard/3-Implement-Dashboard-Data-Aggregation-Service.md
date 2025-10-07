# Actionable Step: Implement Dashboard Data Aggregation Service

**Objective:** Implement the core service layer that aggregates loan and payment data to produce dashboard summary statistics.

**Prerequisites:** 
- Actionable Step 2 (Design Dashboard Service Layer) must be completed

**Action Items:**
1. Create DashboardSummaryDTO class in `src/main/java/com/java_template/application/dto/dashboard/DashboardSummaryDTO.java`:
   - Add all fields with proper types (BigDecimal, Integer, nested DTOs)
   - Use Lombok @Data annotation for getters/setters
   - Add Jackson annotations if needed for JSON serialization
2. Create StatusDistributionDTO class in `src/main/java/com/java_template/application/dto/dashboard/StatusDistributionDTO.java`:
   - Add labels field (List<String>)
   - Add values field (List<Integer>)
   - Use Lombok @Data annotation
3. Create PortfolioTrendDTO class in `src/main/java/com/java_template/application/dto/dashboard/PortfolioTrendDTO.java`:
   - Add months field (List<String>)
   - Add values field (List<BigDecimal>)
   - Use Lombok @Data annotation
4. Create MonthlyPaymentsDTO class in `src/main/java/com/java_template/application/dto/dashboard/MonthlyPaymentsDTO.java`:
   - Add months field (List<String>)
   - Add amounts field (List<BigDecimal>)
   - Use Lombok @Data annotation
5. Create DashboardService interface in `src/main/java/com/java_template/application/service/dashboard/DashboardService.java`:
   - Define `DashboardSummaryDTO getDashboardSummary()` method
   - Add JavaDoc documentation explaining the method purpose and caching behavior
6. Create DashboardServiceImpl class in `src/main/java/com/java_template/application/service/dashboard/DashboardServiceImpl.java`:
   - Add @Service annotation for Spring component scanning
   - Inject EntityService via constructor injection
   - Add SLF4J Logger instance
7. Implement calculateTotalPortfolioValue() private method:
   - Retrieve all loans using EntityService.findAll() with Loan.class
   - Use Java Stream to map to principalAmount and sum using BigDecimal.ZERO as identity
   - Handle null principalAmount values gracefully
8. Implement calculateActiveLoansCount() private method:
   - Retrieve all loans using EntityService.findAll()
   - Filter by metadata.state equals "active" or "funded" (case-insensitive)
   - Count filtered results
9. Implement calculateOutstandingPrincipal() private method:
   - Retrieve all loans and filter by active/funded states
   - Sum outstandingPrincipal field using Stream.reduce()
   - Handle null values by treating as BigDecimal.ZERO
10. Implement calculateActiveBorrowersCount() private method:
    - Retrieve all loans and filter by active/funded states
    - Extract partyId field from each loan
    - Use Stream.distinct() to get unique borrower IDs
    - Count distinct values
11. Implement calculateStatusDistribution() private method:
    - Retrieve all loans
    - Group by metadata.state using Collectors.groupingBy()
    - Create StatusDistributionDTO with ordered labels and corresponding counts
    - Ensure all expected states are included (use predefined list: draft, approval_pending, approved, funded, active, matured, settled, rejected, closed)
12. Implement calculatePortfolioTrend() private method:
    - Retrieve all loans
    - Filter loans with non-null fundingDate
    - Group by fundingDate month (YearMonth) using Collectors.groupingBy()
    - For each month, sum principalAmount values
    - Generate last 12 months list (YearMonth.now() going back 11 months)
    - Create PortfolioTrendDTO with months in "YYYY-MM" format and corresponding values
    - Fill missing months with BigDecimal.ZERO
13. Implement calculateAprDistribution() private method:
    - Retrieve all loans
    - Extract apr field from each loan
    - Filter out null values
    - Return as List<BigDecimal>
14. Implement calculateMonthlyPayments() private method:
    - Retrieve all payments using EntityService.findAll() with Payment.class
    - Filter payments with valueDate in last 6 months
    - Group by valueDate month using Collectors.groupingBy()
    - For each month, sum paymentAmount values
    - Create MonthlyPaymentsDTO with months in "YYYY-MM" format and corresponding amounts
    - Fill missing months with BigDecimal.ZERO
15. Implement getDashboardSummary() public method:
    - Call all calculation methods
    - Assemble DashboardSummaryDTO with all calculated values
    - Add try-catch block to handle exceptions
    - Log errors with context (method name, error message)
    - Return DTO with zeros/empty arrays if errors occur
16. Add error handling for EntityService exceptions:
    - Catch and log CompletionException from EntityService calls
    - Use CyodaExceptionUtil.extractErrorMessage() for error formatting
    - Ensure graceful degradation (return empty/zero values rather than throwing)

**Acceptance Criteria:**
- All DTO classes are created with proper Lombok annotations and field types
- DashboardService interface is created with clear method signature and JavaDoc
- DashboardServiceImpl is created with @Service annotation and proper dependency injection
- All calculation methods are implemented with correct aggregation logic
- Null values and edge cases are handled gracefully in all calculations
- Error handling is implemented with proper logging
- Code follows existing patterns from other service classes in the codebase
- All methods use Java Streams for efficient data processing
- Time-based calculations correctly handle date filtering and grouping

