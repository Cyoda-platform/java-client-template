# Actionable Step: Implement Unit Tests

**Objective:** Create unit tests for the DashboardService and DashboardController to ensure correct functionality and error handling.

**Prerequisites:** 
- Actionable Step 5 (Create Dashboard Controller and REST Endpoint) must be completed

**Action Items:**
1. Create DashboardServiceImplTest class in `src/test/java/com/java_template/application/service/dashboard/DashboardServiceImplTest.java`:
   - Add @ExtendWith(MockitoExtension.class) annotation
   - Add class-level JavaDoc explaining test scope
2. Set up test fixtures in DashboardServiceImplTest:
   - Mock EntityService using @Mock annotation
   - Create @InjectMocks DashboardServiceImpl instance
   - Create sample Loan entities with various states (draft, active, funded, closed)
   - Create sample Payment entities with different dates
   - Create EntityWithMetadata wrappers for test entities
   - Use @BeforeEach to initialize test data
3. Implement test for calculateTotalPortfolioValue:
   - Test name: "testCalculateTotalPortfolioValue_shouldSumAllLoanPrincipals"
   - Mock EntityService.findAll() to return test loans
   - Call getDashboardSummary()
   - Assert totalPortfolioValue equals sum of all loan principals
   - Use BigDecimal.compareTo() for assertions
4. Implement test for calculateActiveLoansCount:
   - Test name: "testCalculateActiveLoansCount_shouldCountActiveAndFundedLoans"
   - Mock EntityService.findAll() with loans in various states
   - Call getDashboardSummary()
   - Assert activeLoansCount equals count of loans with state "active" or "funded"
5. Implement test for calculateOutstandingPrincipal:
   - Test name: "testCalculateOutstandingPrincipal_shouldSumOutstandingForActiveLoans"
   - Mock EntityService.findAll() with loans having outstandingPrincipal values
   - Call getDashboardSummary()
   - Assert outstandingPrincipal equals sum for active/funded loans only
6. Implement test for calculateActiveBorrowersCount:
   - Test name: "testCalculateActiveBorrowersCount_shouldCountDistinctBorrowers"
   - Create loans with duplicate partyId values
   - Mock EntityService.findAll()
   - Call getDashboardSummary()
   - Assert activeBorrowersCount equals distinct count of partyId
7. Implement test for calculateStatusDistribution:
   - Test name: "testCalculateStatusDistribution_shouldGroupLoansByState"
   - Create loans with various states
   - Mock EntityService.findAll()
   - Call getDashboardSummary()
   - Assert statusDistribution labels and values match expected counts
8. Implement test for calculatePortfolioTrend:
   - Test name: "testCalculatePortfolioTrend_shouldAggregateByMonth"
   - Create loans with fundingDate spread across multiple months
   - Mock EntityService.findAll()
   - Call getDashboardSummary()
   - Assert portfolioTrend contains correct months and values
   - Verify last 12 months are included
9. Implement test for calculateAprDistribution:
   - Test name: "testCalculateAprDistribution_shouldReturnAllAprValues"
   - Create loans with various APR values
   - Mock EntityService.findAll()
   - Call getDashboardSummary()
   - Assert aprDistribution contains all APR values
10. Implement test for calculateMonthlyPayments:
    - Test name: "testCalculateMonthlyPayments_shouldAggregatePaymentsByMonth"
    - Create payments with valueDate in last 6 months
    - Mock EntityService.findAll() for Payment.class
    - Call getDashboardSummary()
    - Assert monthlyPayments contains correct months and amounts
11. Implement test for caching behavior:
    - Test name: "testGetDashboardSummary_shouldUseCacheWithinTTL"
    - Call getDashboardSummary() twice within TTL period
    - Verify EntityService.findAll() is called only once
    - Assert both calls return same data
12. Implement test for cache expiration:
    - Test name: "testGetDashboardSummary_shouldRefreshCacheAfterTTL"
    - Call getDashboardSummary()
    - Use reflection or Thread.sleep() to simulate TTL expiration
    - Call getDashboardSummary() again
    - Verify EntityService.findAll() is called twice
13. Implement test for error handling:
    - Test name: "testGetDashboardSummary_shouldHandleEntityServiceException"
    - Mock EntityService.findAll() to throw exception
    - Call getDashboardSummary()
    - Assert method returns DTO with zero/empty values
    - Verify no exception is thrown
14. Implement test for null handling:
    - Test name: "testGetDashboardSummary_shouldHandleNullValues"
    - Create loans with null principalAmount, outstandingPrincipal, apr
    - Mock EntityService.findAll()
    - Call getDashboardSummary()
    - Assert calculations handle nulls gracefully (treat as zero)
15. Create DashboardControllerTest class in `src/test/java/com/java_template/application/controller/DashboardControllerTest.java`:
    - Add @WebMvcTest(DashboardController.class) annotation
    - Inject MockMvc using @Autowired
    - Mock DashboardService using @MockBean
    - Add ObjectMapper using @Autowired
16. Implement controller test for successful response:
    - Test name: "testGetDashboardSummary_shouldReturn200WithData"
    - Mock dashboardService.getDashboardSummary() to return test DTO
    - Perform GET request to "/ui/dashboard/summary"
    - Assert status is 200 OK
    - Assert response body contains expected JSON fields
    - Use jsonPath() assertions for nested fields
17. Implement controller test for error handling:
    - Test name: "testGetDashboardSummary_shouldReturn500OnError"
    - Mock dashboardService.getDashboardSummary() to throw exception
    - Perform GET request to "/ui/dashboard/summary"
    - Assert status is 500 Internal Server Error
    - Assert response contains ProblemDetail structure
18. Implement controller test for JSON serialization:
    - Test name: "testGetDashboardSummary_shouldSerializeCorrectly"
    - Mock dashboardService with complete DTO
    - Perform GET request
    - Assert all nested objects serialize correctly
    - Verify arrays are properly formatted

**Acceptance Criteria:**
- DashboardServiceImplTest class is created with proper test setup
- All calculation methods have corresponding unit tests
- Tests verify correct aggregation logic for each metric
- Caching behavior is tested (cache hit and cache expiration)
- Error handling is tested (exceptions don't propagate)
- Null value handling is tested
- DashboardControllerTest class is created with MockMvc setup
- Controller tests verify 200 OK response with correct data
- Controller tests verify 500 error response on exceptions
- JSON serialization is tested for all nested structures
- All tests follow existing test patterns from AccrualControllerTest, EntityServiceImplTest
- Tests use proper assertions (assertEquals, assertNotNull, jsonPath)
- Test coverage is sufficient (all public methods and error paths)

