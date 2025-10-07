# Open Items - Accruals & GL Batch Implementation

## Overview
This document tracks all known gaps, TODOs, and future enhancements for the Accruals and GL Batch implementation.

---

## Step 2: Accrual Domain Entity - COMPLETED ✅
All items from Step 2 are complete. No open items.

---

## Step 3: Accrual Workflow Criteria Functions - COMPLETED ✅ (with TODOs)

### IsBusinessDayCriterion
**Priority: Medium**

- [ ] **INFRA-001**: Integrate with business calendar service
  - **Current**: Uses hardcoded US federal holidays for 2025
  - **Required**: Dynamic calendar service integration
  - **Impact**: Cannot handle multi-year scenarios or different calendar types
  - **Effort**: Medium (requires calendar service design/integration)

- [ ] **INFRA-002**: Support multiple calendar types
  - **Current**: Only US federal holidays
  - **Required**: Support US, UK, EU, and other regional calendars
  - **Impact**: Cannot support international operations
  - **Effort**: Medium (depends on INFRA-001)

- [ ] **TEST-001**: Add integration tests for business day validation
  - **Current**: Only basic supports() tests
  - **Required**: Tests for weekends, holidays, edge cases
  - **Impact**: Limited test coverage
  - **Effort**: Small

### LoanActiveOnDateCriterion
**Priority: High**

- [ ] **ENTITY-001**: Add NON_ACCRUAL status field to Loan entity
  - **Current**: Loan entity lacks NON_ACCRUAL status field
  - **Required**: Add field and update validation logic
  - **Impact**: Cannot properly handle non-accrual loans
  - **Effort**: Small (entity field addition)

- [ ] **ENTITY-002**: Add charge-off status field to Loan entity
  - **Current**: Loan entity lacks charge-off status field
  - **Required**: Add field and update validation logic
  - **Impact**: Cannot properly handle charged-off loans
  - **Effort**: Small (entity field addition)

- [ ] **LOGIC-001**: Implement NON_ACCRUAL status check
  - **Current**: TODO comment in code
  - **Required**: Check if loan is in NON_ACCRUAL state
  - **Impact**: May accrue interest on non-accrual loans
  - **Effort**: Small (depends on ENTITY-001)

- [ ] **LOGIC-002**: Implement charge-off status check
  - **Current**: TODO comment in code
  - **Required**: Check if loan has been charged off
  - **Impact**: May accrue interest on charged-off loans
  - **Effort**: Small (depends on ENTITY-002)

- [ ] **TEST-002**: Add integration tests for loan active validation
  - **Current**: Only basic supports() tests
  - **Required**: Tests for various loan states and date scenarios
  - **Impact**: Limited test coverage
  - **Effort**: Medium (requires mock EntityService setup)

### NotDuplicateAccrualCriterion
**Priority: Medium**

- [ ] **TEST-003**: Add integration tests for duplicate detection
  - **Current**: Only basic supports() tests
  - **Required**: Tests for:
    - No duplicate exists (success)
    - Duplicate in non-terminal state (failure)
    - Duplicate in SUPERSEDED state (success)
    - Duplicate in FAILED state (success)
    - Duplicate in CANCELED state (success)
    - Superseding scenario (success)
  - **Impact**: Limited test coverage for critical idempotency logic
  - **Effort**: Medium (requires mock EntityService with search)

- [ ] **TEST-004**: Add performance tests for duplicate search
  - **Current**: No performance testing
  - **Required**: Verify search performance with large accrual datasets
  - **Impact**: May have performance issues at scale
  - **Effort**: Medium

### SubledgerAvailableCriterion
**Priority: High**

- [ ] **INFRA-003**: Design and implement sub-ledger service interface
  - **Current**: Stub methods (isSubledgerHealthy, areAccountsConfigured)
  - **Required**: Actual sub-ledger service integration
  - **Impact**: Cannot verify sub-ledger availability
  - **Effort**: Large (requires service design and implementation)

- [ ] **INFRA-004**: Implement sub-ledger health check endpoint
  - **Current**: Always returns true
  - **Required**: HTTP/gRPC call to sub-ledger health endpoint
  - **Impact**: Cannot detect sub-ledger outages
  - **Effort**: Medium (depends on INFRA-003)

- [ ] **INFRA-005**: Implement GL account configuration check
  - **Current**: Hardcoded currency list
  - **Required**: Query sub-ledger for account configuration
  - **Impact**: Cannot verify accounts are properly configured
  - **Effort**: Medium (depends on INFRA-003)

- [ ] **TEST-005**: Add integration tests for sub-ledger availability
  - **Current**: Only basic supports() tests
  - **Required**: Tests with mock sub-ledger service
  - **Impact**: Limited test coverage
  - **Effort**: Medium (depends on INFRA-003)

### RequiresRebookCriterion
**Priority: High**

- [ ] **LOGIC-003**: Implement full interest recalculation logic
  - **Current**: Simplified principal comparison only
  - **Required**: Complete interest calculation using current loan data
  - **Impact**: May miss rebook scenarios or trigger unnecessary rebooks
  - **Effort**: Large (requires interest calculation engine)

- [ ] **LOGIC-004**: Implement APR change detection
  - **Current**: TODO comment in code
  - **Required**: Compare current APR with snapshot APR
  - **Impact**: May miss rebook scenarios when APR changes
  - **Effort**: Small (depends on LOGIC-003)

- [ ] **LOGIC-005**: Implement day count convention change detection
  - **Current**: TODO comment in code
  - **Required**: Compare current convention with snapshot convention
  - **Impact**: May miss rebook scenarios when convention changes
  - **Effort**: Small (depends on LOGIC-003)

- [ ] **CONFIG-001**: Make materiality threshold configurable
  - **Current**: Hardcoded to 0.01
  - **Required**: Externalize to configuration
  - **Impact**: Cannot adjust threshold without code changes
  - **Effort**: Small

- [ ] **TEST-006**: Add integration tests for rebook requirement logic
  - **Current**: Only basic supports() tests
  - **Required**: Tests for various change scenarios
  - **Impact**: Limited test coverage for critical rebook logic
  - **Effort**: Medium (requires mock EntityService)

### General Criteria TODOs
**Priority: Medium**

- [ ] **PERF-001**: Add performance tests for EntityService queries
  - **Current**: No performance testing
  - **Required**: Verify query performance at scale
  - **Impact**: May have performance issues with large datasets
  - **Effort**: Medium

- [ ] **CACHE-001**: Consider caching for frequently accessed data
  - **Current**: No caching
  - **Required**: Cache business calendar, loan data, etc.
  - **Impact**: Repeated queries may impact performance
  - **Effort**: Medium

- [ ] **TEST-007**: Increase test coverage to 80%+
  - **Current**: Basic unit tests only (~20% coverage estimate)
  - **Required**: Comprehensive integration tests
  - **Impact**: Limited confidence in criterion behavior
  - **Effort**: Large

---

## Step 4: Accrual Workflow Processors - COMPLETED ✅ (with TODOs)

### DeriveDayCountFractionProcessor
**Priority: Medium**

- [ ] **CALC-001**: Integrate with business calendar service for previous business day
  - **Current**: Uses simple asOfDate - 1
  - **Required**: Use business calendar to skip weekends/holidays
  - **Impact**: May calculate incorrect day count for Monday accruals
  - **Effort**: Small (depends on business calendar service)

- [ ] **CALC-002**: Support additional 30/360 variants
  - **Current**: Implements basic 30/360 (US)
  - **Required**: Support European, ISDA, and other variants
  - **Impact**: Limited to US convention
  - **Effort**: Medium

### CalculateAccrualAmountProcessor
**Priority: High**

- [ ] **CALC-003**: Optimize APR retrieval
  - **Current**: Queries Loan entity for every accrual
  - **Required**: Cache APR or include in principal snapshot
  - **Impact**: Performance overhead for large batches
  - **Effort**: Medium

- [ ] **CALC-004**: Support variable rate products
  - **Current**: Assumes fixed APR
  - **Required**: Handle rate changes within accrual period
  - **Impact**: Cannot handle variable rate loans
  - **Effort**: Large

### WriteAccrualJournalEntriesProcessor
**Priority: Low**

- [ ] **ENTRY-001**: Support additional account types
  - **Current**: Only INTEREST_RECEIVABLE and INTEREST_INCOME
  - **Required**: Support fees, penalties, etc.
  - **Impact**: Limited to interest accruals
  - **Effort**: Small

### UpdateLoanAccruedInterestProcessor
**Priority: Medium**

- [ ] **LOAN-001**: Add concurrency control
  - **Current**: No optimistic locking
  - **Required**: Handle concurrent updates to loan
  - **Impact**: Risk of lost updates in high-concurrency scenarios
  - **Effort**: Medium

### ReversePriorJournalsProcessor
**Priority: Low**

- [ ] **REV-001**: Support partial reversals
  - **Current**: Reverses all ORIGINAL entries
  - **Required**: Support selective reversal
  - **Impact**: Cannot handle partial corrections
  - **Effort**: Medium

### CreateReplacementAccrualProcessor
**Priority: Low**

- [ ] **REPL-001**: Optimize replacement workflow
  - **Current**: New accrual goes through full workflow
  - **Required**: Consider pre-calculating in processor
  - **Impact**: Additional workflow overhead
  - **Effort**: Large (requires workflow redesign)

### General Processor TODOs
**Priority: High**

- [ ] **TEST-011**: Add comprehensive integration tests
  - **Current**: Only basic supports() tests
  - **Required**: Full processing logic tests with mock data
  - **Impact**: Limited test coverage
  - **Effort**: Large

- [ ] **PERF-002**: Add performance benchmarks
  - **Current**: No performance testing
  - **Required**: Benchmark with realistic data volumes
  - **Impact**: Unknown performance characteristics
  - **Effort**: Medium

- [ ] **MON-006**: Add processor execution metrics
  - **Current**: Only logging
  - **Required**: Metrics for execution time, success/failure rates
  - **Impact**: Limited observability
  - **Effort**: Small

---

## Step 5: EODAccrualBatch Domain Entity - COMPLETED ✅
All items from Step 5 are complete. No open items.

---

## Step 6: EODAccrualBatch Workflow Criteria Functions - COMPLETED ✅ (with TODOs)

### UserHasPermissionCriterion
**Priority: High**

- [ ] **PERM-001**: Integrate with actual permission/authorization service
  - **Current**: Placeholder permission check (always returns true)
  - **Required**: Integration with real permission service
  - **Impact**: Cannot enforce permission-based access control
  - **Effort**: Medium (requires permission service design/integration)

### CascadeSettledCriterion
**Priority: Medium**

- [ ] **CASCADE-001**: Implement proper cascade relationship tracking
  - **Current**: Simple date-based filtering (asOfDate >= cascadeFromDate)
  - **Required**: Track actual cascade relationships between batches
  - **Impact**: May incorrectly determine cascade completion
  - **Effort**: Medium (requires cascade tracking design)

### General EOD Batch Criteria TODOs
**Priority: Medium**

- [ ] **TEST-EOD-001**: Add integration tests for all EOD batch criteria
  - **Current**: Only basic supports() tests
  - **Required**: Full evaluation logic tests with mock EntityService
  - **Impact**: Limited test coverage
  - **Effort**: Large

---

## Step 7: EODAccrualBatch Workflow Processors - NOT STARTED ⏳

- [ ] **PROC-EOD-001**: Implement CaptureEffectiveDatedSnapshotsProcessor
- [ ] **PROC-EOD-002**: Implement ResolvePeriodStatusProcessor
- [ ] **PROC-EOD-003**: Implement SpawnAccrualsForEligibleLoansProcessor
- [ ] **PROC-EOD-004**: Implement SpawnCascadeRecalcProcessor
- [ ] **PROC-EOD-005**: Implement ProduceReconciliationReportProcessor
- [ ] **TEST-EOD-002**: Create unit tests for all EOD batch processors

---

## Step 8: Accrual Workflow Configuration - NOT STARTED ⏳

- [ ] **WF-001**: Create Accrual workflow JSON configuration
- [ ] **WF-002**: Define Accrual state transitions
- [ ] **WF-003**: Map Accrual criteria to transitions
- [ ] **WF-004**: Map Accrual processors to transitions
- [ ] **WF-005**: Import Accrual workflow to Cyoda
- [ ] **WF-006**: Test Accrual workflow execution

---

## Step 9: EODAccrualBatch Workflow Configuration - NOT STARTED ⏳

- [ ] **WF-EOD-001**: Create EODAccrualBatch workflow JSON configuration
- [ ] **WF-EOD-002**: Define EODAccrualBatch state transitions
- [ ] **WF-EOD-003**: Map EODAccrualBatch criteria to transitions
- [ ] **WF-EOD-004**: Map EODAccrualBatch processors to transitions
- [ ] **WF-EOD-005**: Import EODAccrualBatch workflow to Cyoda
- [ ] **WF-EOD-006**: Test EODAccrualBatch workflow execution

---

## Cross-Cutting Concerns

### Documentation
**Priority: Medium**

- [ ] **DOC-001**: Create API documentation for criteria
- [ ] **DOC-002**: Create API documentation for processors
- [ ] **DOC-003**: Create workflow configuration guide
- [ ] **DOC-004**: Create EOD orchestration runbook
- [ ] **DOC-005**: Create troubleshooting guide

### Monitoring & Observability
**Priority: High**

- [ ] **MON-001**: Add metrics for criterion evaluation times
- [ ] **MON-002**: Add metrics for processor execution times
- [ ] **MON-003**: Add metrics for workflow completion rates
- [ ] **MON-004**: Add metrics for EOD batch processing
- [ ] **MON-005**: Add alerting for failures and anomalies

### Security
**Priority: High**

- [ ] **SEC-001**: Review and implement authorization checks
- [ ] **SEC-002**: Implement audit logging for accrual operations
- [ ] **SEC-003**: Implement data encryption for sensitive fields
- [ ] **SEC-004**: Review and implement rate limiting

### Data Quality
**Priority: Medium**

- [ ] **DQ-001**: Implement data validation rules
- [ ] **DQ-002**: Implement data reconciliation checks
- [ ] **DQ-003**: Implement data quality metrics
- [ ] **DQ-004**: Implement data quality alerting

### Scalability
**Priority: Medium**

- [ ] **SCALE-001**: Performance test with 1M+ loans
- [ ] **SCALE-002**: Optimize EntityService queries
- [ ] **SCALE-003**: Implement parallel processing for EOD batch
- [ ] **SCALE-004**: Implement database indexing strategy

---

## Priority Legend
- **High**: Blocks core functionality or has significant business impact
- **Medium**: Important for production readiness but not blocking
- **Low**: Nice to have or future enhancement

## Status Legend
- ✅ **COMPLETED**: Implementation done and tested
- ⏳ **NOT STARTED**: Not yet begun
- 🚧 **IN PROGRESS**: Currently being worked on
- ⚠️ **BLOCKED**: Waiting on dependency or decision

---

## Notes
- This list will be updated as implementation progresses
- New items should be added with unique IDs following the pattern: `CATEGORY-###`
- Categories: ENTITY, LOGIC, INFRA, TEST, PROC, WF, EOD, DOC, MON, SEC, DQ, SCALE, CONFIG, CACHE, PERF
- Items should include: Current state, Required state, Impact, and Effort estimate

---

**Last Updated**: 2025-10-06 (Step 6 completion)
**Next Review**: After Step 7 completion

