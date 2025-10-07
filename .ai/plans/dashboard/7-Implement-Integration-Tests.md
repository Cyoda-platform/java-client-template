# Actionable Step: Implement Integration Tests

**Objective:** Create integration tests that verify the dashboard endpoint works correctly with real EntityService and actual data flow.

**Prerequisites:** 
- Actionable Step 6 (Implement Unit Tests) must be completed

**Action Items:**
1. Create DashboardIntegrationTest class in `src/test/java/com/java_template/application/integration/DashboardIntegrationTest.java`:
   - Add @SpringBootTest annotation for full application context
   - Add @AutoConfigureMockMvc annotation for MockMvc support
   - Add class-level JavaDoc explaining integration test scope
2. Set up test fixtures:
   - Inject MockMvc using @Autowired
   - Inject EntityService using @Autowired
   - Inject ObjectMapper using @Autowired
   - Create test data setup method with @BeforeEach
3. Implement test data creation helper methods:
   - createTestLoan() method to create Loan entities with various states
   - createTestPayment() method to create Payment entities with different dates
   - createTestParty() method to create Party entities for borrowers
4. Implement setup method to populate test data:
   - Create and save multiple loans with different states (draft, active, funded, closed)
   - Create and save multiple payments with dates in last 6 months
   - Create and save multiple parties as borrowers
   - Store entity IDs for cleanup
5. Implement cleanup method with @AfterEach:
   - Delete all test entities created during setup
   - Use EntityService.delete() or appropriate cleanup method
   - Ensure clean state for next test
6. Implement integration test for full dashboard flow:
   - Test name: "testGetDashboardSummary_withRealData_shouldReturnCorrectAggregations"
   - Create 10 test loans with various states and amounts
   - Create 20 test payments with different dates
   - Perform GET request to "/ui/dashboard/summary"
   - Assert status is 200 OK
   - Assert totalPortfolioValue matches sum of test loan principals
   - Assert activeLoansCount matches count of active/funded test loans
   - Assert outstandingPrincipal matches sum for active loans
   - Assert activeBorrowersCount matches distinct borrower count
7. Implement integration test for status distribution:
   - Test name: "testGetDashboardSummary_shouldReturnCorrectStatusDistribution"
   - Create loans with known state distribution (2 draft, 3 active, 1 funded, 1 closed)
   - Perform GET request
   - Assert statusDistribution labels contain all states
   - Assert statusDistribution values match expected counts
8. Implement integration test for portfolio trend:
   - Test name: "testGetDashboardSummary_shouldReturnPortfolioTrendForLast12Months"
   - Create loans with fundingDate spread across last 12 months
   - Perform GET request
   - Assert portfolioTrend contains 12 months
   - Assert months are in "YYYY-MM" format
   - Assert values match expected monthly sums
9. Implement integration test for monthly payments:
   - Test name: "testGetDashboardSummary_shouldReturnMonthlyPaymentsForLast6Months"
   - Create payments with valueDate in last 6 months
   - Perform GET request
   - Assert monthlyPayments contains 6 months
   - Assert amounts match expected monthly payment sums
10. Implement integration test for APR distribution:
    - Test name: "testGetDashboardSummary_shouldReturnAllAprValues"
    - Create loans with specific APR values (4.5, 5.2, 3.8, 6.0)
    - Perform GET request
    - Assert aprDistribution contains all expected APR values
11. Implement integration test for empty data scenario:
    - Test name: "testGetDashboardSummary_withNoData_shouldReturnZerosAndEmptyArrays"
    - Skip test data creation (or delete all entities)
    - Perform GET request
    - Assert status is 200 OK
    - Assert totalPortfolioValue is 0
    - Assert activeLoansCount is 0
    - Assert arrays are empty
12. Implement integration test for caching behavior:
    - Test name: "testGetDashboardSummary_shouldCacheResults"
    - Perform first GET request and record response time
    - Perform second GET request immediately after
    - Assert both responses are identical
    - Assert second request is faster (cached)
13. Implement integration test for cache expiration:
    - Test name: "testGetDashboardSummary_shouldRefreshAfterTTL"
    - Perform first GET request
    - Create new loan entity
    - Wait for cache TTL to expire (or use cache invalidation if available)
    - Perform second GET request
    - Assert second response includes new loan data
14. Implement integration test for concurrent requests:
    - Test name: "testGetDashboardSummary_shouldHandleConcurrentRequests"
    - Use ExecutorService to make multiple concurrent requests
    - Assert all requests return 200 OK
    - Assert all responses are consistent
    - Verify no race conditions in caching
15. Implement integration test for large data volumes:
    - Test name: "testGetDashboardSummary_withLargeDataset_shouldPerformAcceptably"
    - Create 1000 test loans
    - Create 5000 test payments
    - Perform GET request
    - Assert response time is acceptable (< 5 seconds)
    - Assert calculations are correct
16. Add performance logging:
    - Log execution time for each integration test
    - Log cache hit/miss statistics
    - Document performance benchmarks in test comments

**Acceptance Criteria:**
- DashboardIntegrationTest class is created with proper Spring Boot test setup
- Test data creation and cleanup methods are implemented
- Integration test verifies full dashboard flow with real data
- All aggregation metrics are tested with actual EntityService calls
- Status distribution test verifies correct grouping
- Portfolio trend test verifies 12-month time series
- Monthly payments test verifies 6-month aggregation
- APR distribution test verifies all values are included
- Empty data scenario is tested
- Caching behavior is verified with integration tests
- Cache expiration is tested
- Concurrent request handling is tested
- Large dataset performance is tested
- All tests pass consistently
- Tests follow existing integration test patterns from the codebase
- Performance benchmarks are documented

