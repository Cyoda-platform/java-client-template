# Actionable Step: Analyze Existing Patterns and Data Model

**Objective:** Understand the existing codebase patterns, data models, and architectural conventions to ensure the dashboard implementation follows established practices.

**Prerequisites:** None.

**Action Items:**
1. Review existing controller patterns in `src/main/java/com/java_template/application/controller/` to understand error handling, response formatting, and logging conventions
2. Examine the Loan entity structure in `src/main/java/com/java_template/application/entity/loan/version_1/Loan.java` to identify all relevant fields for dashboard calculations (principalAmount, apr, outstandingPrincipal, fundingDate, etc.)
3. Review the Payment entity structure in `src/main/java/com/java_template/application/entity/payment/version_1/Payment.java` to understand payment data model (paymentAmount, valueDate, allocation fields)
4. Examine the Party entity structure in `src/main/java/com/java_template/application/entity/party/version_1/Party.java` to understand borrower relationships
5. Review the Loan workflow states in `src/main/resources/workflow/loan/version_1/Loan.json` to confirm all status values (initial, draft, approval_pending, approved, funded, active, matured, settled, rejected, closed)
6. Study EntityService interface in `src/main/java/com/java_template/common/service/EntityService.java` to understand available query methods (findAll, search, getEntityCount)
7. Examine existing search patterns using GroupCondition and SimpleCondition in controllers like `AccrualController.java` and `PartyController.java`
8. Review the metadata structure in `EntityWithMetadata` to understand how to access workflow state information
9. Identify any existing caching patterns in the codebase (check Authentication.java for token caching example using ConcurrentHashMap)
10. Document the required data points and their sources:
    - totalPortfolioValue: Sum of Loan.principalAmount across all loans
    - activeLoansCount: Count of loans with metadata.state = "active" OR "funded"
    - outstandingPrincipal: Sum of Loan.outstandingPrincipal for active/funded loans
    - activeBorrowersCount: Distinct count of Loan.partyId for active/funded loans
    - statusDistribution: Count of loans grouped by metadata.state
    - portfolioTrend: Monthly sum of principalAmount over last 12 months (requires fundingDate filtering)
    - aprDistribution: Array of Loan.apr values for all loans
    - monthlyPayments: Sum of Payment.paymentAmount grouped by Payment.valueDate month for last 6 months

**Acceptance Criteria:**
- All relevant entity fields and their data types are documented
- All Loan workflow states are confirmed and listed
- EntityService query methods suitable for dashboard data retrieval are identified
- Existing controller patterns for error handling and response formatting are documented
- Data aggregation requirements are clearly mapped to entity fields and query strategies
- A clear understanding of how to filter entities by workflow state using metadata is established

