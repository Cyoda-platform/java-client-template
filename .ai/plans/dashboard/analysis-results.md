# Dashboard Implementation - Analysis Results

**Date:** 2025-10-07  
**Status:** ✅ Complete

This document captures the findings from analyzing existing patterns and data models to inform the dashboard implementation.

---

## 1. Controller Patterns

### Error Handling
- **ProblemDetail Pattern**: Controllers use Spring's `ProblemDetail.forStatusAndDetail()` for structured error responses
- **Try-Catch Blocks**: All endpoints wrapped in try-catch with appropriate HTTP status codes
- **Logging**: SLF4J logger used for info, warn, and error messages
- **Response Format**: `ResponseEntity.of(problemDetail).build()` for error responses

### Response Formatting
- **Success Responses**: Return `ResponseEntity.ok(data)` for 200 OK
- **Created Responses**: Return `ResponseEntity.created(location).body(response)` with Location header
- **Not Found**: Return `ResponseEntity.notFound().build()` for 404

### Logging Conventions
- Logger declaration: `private static final Logger logger = LoggerFactory.getLogger(ClassName.class);`
- Info level: Successful operations (create, update, delete)
- Warn level: Business validation failures (duplicates, conflicts)
- Error level: Exceptions with stack traces

### Common Patterns
- **ObjectMapper**: Injected for JSON conversions (especially for `valueToTree()` in search conditions)
- **EntityService**: Injected for all entity operations
- **CORS**: `@CrossOrigin(origins = "*")` on all controllers
- **Request Mapping**: RESTful paths (e.g., `/ui/parties`, `/accruals`)

---

## 2. Loan Entity Structure

**Location:** `src/main/java/com/java_template/application/entity/loan/version_1/Loan.java`

### Core Fields for Dashboard
| Field | Type | Purpose | Dashboard Usage |
|-------|------|---------|-----------------|
| `loanId` | String | Business identifier | Filtering, display |
| `partyId` | String | Reference to borrower | Borrower counting |
| `principalAmount` | BigDecimal | Original loan amount | Portfolio value calculation |
| `apr` | BigDecimal | Annual Percentage Rate | APR distribution |
| `termMonths` | Integer | Loan term (12/24/36) | Analytics |
| `fundingDate` | LocalDate | Date loan was funded | Portfolio trend (monthly grouping) |
| `maturityDate` | LocalDate | Loan maturity date | Maturity tracking |
| `outstandingPrincipal` | BigDecimal | Current principal balance | Outstanding principal sum |
| `accruedInterest` | BigDecimal | Current accrued interest | Interest tracking |

### Nested Structures
- `LoanParty`: Party details with roles (Borrower, Lender, Agent)
- `LoanFacility`: Facility details with tranches, drawdowns, repayments
- Complex nested structure for commercial loan details

### Validation
- Required fields validated in `isValid()` method
- Principal amount must be > 0
- APR must be > 0
- Term months must be 12, 24, or 36

---

## 3. Payment Entity Structure

**Location:** `src/main/java/com/java_template/application/entity/payment/version_1/Payment.java`

### Core Fields for Dashboard
| Field | Type | Purpose | Dashboard Usage |
|-------|------|---------|-----------------|
| `paymentId` | String | Business identifier | Filtering |
| `loanId` | String | Reference to loan | Linking payments to loans |
| `payerPartyId` | String | Reference to payer | Payer tracking |
| `paymentAmount` | BigDecimal | Total payment amount | Monthly payment sum |
| `valueDate` | LocalDate | Effective date | Monthly grouping for trends |
| `receivedDate` | LocalDate | Actual receipt date | Audit trail |
| `allocation` | PaymentAllocation | Breakdown of payment | Detailed allocation |

### PaymentAllocation Nested Class
- `interestAllocated`: BigDecimal
- `feesAllocated`: BigDecimal
- `principalAllocated`: BigDecimal
- `excessFunds`: BigDecimal (for overpayments)

---

## 4. Party Entity Structure

**Location:** `src/main/java/com/java_template/application/entity/party/version_1/Party.java`

### Core Fields
| Field | Type | Purpose | Dashboard Usage |
|-------|------|---------|-----------------|
| `partyId` | String | Business identifier | Borrower identification |
| `legalName` | String | Legal entity name | Display |
| `jurisdiction` | String | Legal jurisdiction | Filtering |
| `lei` | String | Legal Entity Identifier | Optional identifier |
| `role` | String | Party role | Role-based filtering |

### Nested Structures
- `PartyContact`: Contact information
- `PartyAddress`: Address details

---

## 5. Loan Workflow States

**Location:** `src/main/resources/workflow/loan/version_1/Loan.json`

### All Workflow States
1. **initial** - Starting state
2. **draft** - Loan being drafted
3. **approval_pending** - Submitted for approval
4. **approved** - Approved but not yet funded
5. **funded** - Funded but not yet active
6. **active** - Active loan (accruing interest, accepting payments)
7. **matured** - Reached maturity date
8. **settled** - Final payment received
9. **rejected** - Approval rejected
10. **closed** - Loan closed (terminal state)

### States for "Active Loans" Count
- **active**: Loan is actively accruing interest
- **funded**: Loan is funded but not yet active (funding date not reached)

### Terminal States
- **rejected**: No further transitions
- **closed**: No further transitions

---

## 6. EntityService Query Methods

**Location:** `src/main/java/com/java_template/common/service/EntityService.java`

### Suitable Methods for Dashboard

#### For Retrieving All Loans
```java
List<EntityWithMetadata<Loan>> findAll(ModelSpec modelSpec, Class<Loan> entityClass, Date pointInTime)
```
- Returns all loans with metadata
- Metadata includes workflow state
- **Performance**: SLOW - use sparingly

#### For Counting Entities
```java
long getEntityCount(ModelSpec modelSpec, Date pointInTime)
```
- Returns total count of entities
- **Performance**: FAST - uses statistics API

#### For Filtered Queries
```java
List<EntityWithMetadata<Loan>> search(ModelSpec modelSpec, GroupCondition condition, Class<Loan> entityClass, Date pointInTime)
```
- Complex queries with multiple conditions
- **Performance**: SLOWEST - most flexible

### Search Condition Building
- Use `GroupCondition` with `Operator.AND` or `Operator.OR`
- Use `SimpleCondition` for field comparisons
- JSONPath syntax: `$.fieldName` for entity fields
- Operations: `EQUALS`, `CONTAINS`, `GREATER_THAN`, `LESS_THAN`, etc.
- Convert values using `objectMapper.valueToTree(value)`

---

## 7. EntityWithMetadata Structure

**Location:** `src/main/java/com/java_template/common/dto/EntityWithMetadata.java`

### Structure
```java
public record EntityWithMetadata<T extends CyodaEntity>(
    @JsonProperty("entity") T entity,
    @JsonProperty("meta") EntityMetadata metadata
)
```

### Accessing Workflow State
```java
EntityWithMetadata<Loan> loanWithMeta = ...;
String state = loanWithMeta.metadata().getState();  // "active", "funded", etc.
```

### Convenience Methods
- `getId()`: Technical UUID
- `getState()`: Workflow state
- `getCreationDate()`: Entity creation date
- `getModelKey()`: Model specification

---

## 8. Search Pattern Examples

### Example from AccrualController
```java
List<QueryCondition> conditions = new ArrayList<>();

// Add field filter
conditions.add(new SimpleCondition()
    .withJsonPath("$.loanId")
    .withOperation(Operation.EQUALS)
    .withValue(objectMapper.valueToTree(loanId)));

// Build group condition
GroupCondition groupCondition = new GroupCondition()
    .withOperator(GroupCondition.Operator.AND)
    .withConditions(conditions);

// Execute search
List<EntityWithMetadata<Accrual>> results = entityService.search(
    modelSpec, groupCondition, Accrual.class, null);

// Filter by state (metadata field)
if (state != null) {
    results = results.stream()
        .filter(accrual -> state.equals(accrual.metadata().getState()))
        .toList();
}
```

### Key Pattern: State Filtering
- **Entity fields**: Filter using `search()` with `SimpleCondition`
- **Metadata fields** (like state): Filter in-memory after retrieval using `stream().filter()`

---

## 9. Caching Patterns

**Location:** `src/main/java/com/java_template/common/auth/Authentication.java`

### ConcurrentHashMap Pattern
```java
private final ConcurrentMap<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

// Atomic compute operation
CachedToken token = tokenCache.compute(CACHE_KEY, (key, existing) -> {
    if (existing != null && existing.isValid()) {
        return existing;  // Reuse cached value
    }
    // Fetch new value
    return new CachedToken(fetchNewToken());
});
```

### Cache Invalidation
```java
public void invalidateCache() {
    tokenCache.remove(CACHE_KEY);
}
```

### Applicability to Dashboard
- Use `ConcurrentHashMap` for thread-safe caching
- Implement time-based expiry (e.g., cache for 5 minutes)
- Use `compute()` for atomic cache updates
- Provide manual invalidation method

---

## 10. Dashboard Data Requirements Mapping

### Data Point: Total Portfolio Value
- **Source**: Sum of `Loan.principalAmount` across all loans
- **Query**: `findAll()` for Loan entities
- **Calculation**: `loans.stream().map(l -> l.entity().getPrincipalAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)`

### Data Point: Active Loans Count
- **Source**: Count of loans with `metadata.state` = "active" OR "funded"
- **Query**: `findAll()` for Loan entities, filter by state
- **Calculation**: `loans.stream().filter(l -> "active".equals(l.getState()) || "funded".equals(l.getState())).count()`

### Data Point: Outstanding Principal
- **Source**: Sum of `Loan.outstandingPrincipal` for active/funded loans
- **Query**: `findAll()` for Loan entities, filter by state
- **Calculation**: Filter active/funded, then sum `outstandingPrincipal`

### Data Point: Active Borrowers Count
- **Source**: Distinct count of `Loan.partyId` for active/funded loans
- **Query**: `findAll()` for Loan entities, filter by state
- **Calculation**: `loans.stream().filter(active/funded).map(l -> l.entity().getPartyId()).distinct().count()`

### Data Point: Status Distribution
- **Source**: Count of loans grouped by `metadata.state`
- **Query**: `findAll()` for Loan entities
- **Calculation**: `loans.stream().collect(Collectors.groupingBy(EntityWithMetadata::getState, Collectors.counting()))`

### Data Point: Portfolio Trend (Last 12 Months)
- **Source**: Monthly sum of `Loan.principalAmount` where `fundingDate` in last 12 months
- **Query**: `findAll()` for Loan entities
- **Calculation**: Filter by fundingDate, group by month, sum principalAmount

### Data Point: APR Distribution
- **Source**: Array of `Loan.apr` values for all loans
- **Query**: `findAll()` for Loan entities
- **Calculation**: `loans.stream().map(l -> l.entity().getApr()).toList()`

### Data Point: Monthly Payments (Last 6 Months)
- **Source**: Sum of `Payment.paymentAmount` grouped by `Payment.valueDate` month
- **Query**: `findAll()` for Payment entities
- **Calculation**: Filter by valueDate (last 6 months), group by month, sum paymentAmount

---

## Summary

### ✅ Acceptance Criteria Met

1. **Entity fields documented**: All relevant fields from Loan, Payment, and Party entities are documented with types and purposes
2. **Workflow states confirmed**: All 10 Loan workflow states identified and documented
3. **EntityService methods identified**: `findAll()`, `getEntityCount()`, and `search()` methods documented with performance characteristics
4. **Controller patterns documented**: Error handling, response formatting, and logging conventions captured
5. **Data aggregation mapped**: All 8 dashboard data points mapped to entity fields and query strategies
6. **Metadata filtering understood**: Clear pattern for filtering by workflow state using `metadata.getState()`

### Key Insights

1. **State filtering requires two-step process**: Use `search()` for entity fields, then in-memory filtering for metadata fields
2. **Performance considerations**: `findAll()` is slow but necessary for dashboard aggregations; consider caching
3. **BigDecimal arithmetic**: All financial calculations use BigDecimal for precision
4. **Thread-safe caching**: Use `ConcurrentHashMap` with `compute()` for atomic operations
5. **Error handling consistency**: All controllers follow same ProblemDetail pattern

### Next Steps

With this analysis complete, the next actionable step is to design the dashboard data model and API endpoints based on these findings.

