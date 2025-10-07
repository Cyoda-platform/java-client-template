# Actionable Step: Implement EODAccrualBatch Workflow Processors

**Objective:** Implement all processor functions required by the EODAccrualBatch workflow as defined in specification sections 4.2 and 5.

**Prerequisites:**
- Actionable Step 5 (Create EODAccrualBatch Domain Entity and Data Model) must be completed.
- Actionable Step 6 (Implement EODAccrualBatch Workflow Criteria Functions) must be completed.

**Action Items:**
1. Review processor specifications in section 5 of cyoda-eod-accrual-workflows.md
2. Review EODAccrualBatch workflow configuration in section 4.2 of cyoda-eod-accrual-workflows.md
3. Review example processor implementations in `llm_example/code/application/processor/`
4. Create `CaptureEffectiveDatedSnapshotsProcessor.java` implementing CyodaProcessor in `src/main/java/com/java_template/application/processor/`
5. Implement process() method to snapshot principal, APR, and policy data at AsOfDate
6. Query loan entities effective as of the batch's asOfDate
7. Store snapshot data in batch or related structure for later use
8. Implement supports() method returning "CaptureEffectiveDatedSnapshots"
9. Mark as ASYNC_NEW_TX execution mode compatible
10. Create `ResolvePeriodStatusProcessor.java` implementing CyodaProcessor
11. Implement process() method to determine GL period status (OPEN/CLOSED) for AsOfDate
12. Query GL calendar or period configuration to determine if period is closed
13. Update batch's periodStatus field
14. Set priorPeriodFlag logic based on period status
15. Implement supports() method returning "ResolvePeriodStatus"
16. Mark as SYNC execution mode
17. Create `SpawnAccrualsForEligibleLoansProcessor.java` implementing CyodaProcessor
18. Implement process() method to fan-out Accruals for ACTIVE loans on AsOfDate
19. Query all loans matching the batch's loanFilter
20. Filter loans that were ACTIVE on asOfDate
21. For each eligible loan, create a new Accrual entity with state NEW
22. Set accrual's runId to the batch's batchId
23. Set accrual's asOfDate to the batch's asOfDate
24. Set accrual's priorPeriodFlag based on batch's periodStatus
25. Use EntityService to create each accrual entity
26. Update batch metrics with eligibleLoans count
27. Implement supports() method returning "SpawnAccrualsForEligibleLoans"
28. Mark as ASYNC_NEW_TX execution mode compatible
29. Create `SpawnCascadeRecalcProcessor.java` implementing CyodaProcessor
30. Implement process() method to recompute forward days and post deltas for back-dated runs
31. Determine cascade date range from batch's asOfDate to current business date
32. For each date in range, identify affected loans
33. Trigger recalculation of accruals for subsequent dates
34. Update batch's cascadeFromDate field
35. Implement supports() method returning "SpawnCascadeRecalc"
36. Mark as ASYNC_NEW_TX execution mode compatible
37. Create `ProduceReconciliationReportProcessor.java` implementing CyodaProcessor
38. Implement process() method to generate summaries and PPA reports
39. Aggregate all journal entries from accruals with matching runId
40. Calculate total debits, credits, and identify imbalances
41. Identify all accruals with priorPeriodFlag=true for PPA section
42. Generate report file (CSV or JSON format)
43. Store report file reference in batch's reportId field
44. Update batch metrics with final totals
45. Implement supports() method returning "ProduceReconciliationReport"
46. Mark as ASYNC_NEW_TX execution mode compatible
47. Use ProcessorSerializer for type-safe entity processing in all processors
48. Ensure processors use EntityService to read current batch entity
49. Ensure processors can update OTHER entities (accruals, loans) but NOT the current batch with EntityService
50. Add proper error handling and logging in all processors
51. Create unit test class `CaptureEffectiveDatedSnapshotsProcessorTest.java`
52. Write tests for snapshot capture with various loan states
53. Create unit test class `ResolvePeriodStatusProcessorTest.java`
54. Write tests for open and closed period scenarios
55. Create unit test class `SpawnAccrualsForEligibleLoansProcessorTest.java`
56. Write tests for fan-out with various loan filters
57. Create unit test class `SpawnCascadeRecalcProcessorTest.java`
58. Write tests for cascade calculation with backdated scenarios
59. Create unit test class `ProduceReconciliationReportProcessorTest.java`
60. Write tests for report generation with balanced and imbalanced batches
61. Run `./gradlew test` to verify all processor tests pass
62. Run `./gradlew build` to verify compilation succeeds

**Acceptance Criteria:**
- All five EODAccrualBatch workflow processors are implemented: CaptureEffectiveDatedSnapshots, ResolvePeriodStatus, SpawnAccrualsForEligibleLoans, SpawnCascadeRecalc, ProduceReconciliationReport
- Each processor implements CyodaProcessor interface correctly
- Each processor's supports() method returns the correct name matching workflow configuration
- ProcessorSerializer is used for type-safe processing
- Processors correctly use EntityService to read current entity and update OTHER entities
- Fan-out logic creates accruals with proper runId and priorPeriodFlag linkage
- Cascade logic handles backdated recalculation correctly
- Reconciliation report aggregates journal entries correctly
- Unit tests exist and pass for all processors
- Code compiles without errors

