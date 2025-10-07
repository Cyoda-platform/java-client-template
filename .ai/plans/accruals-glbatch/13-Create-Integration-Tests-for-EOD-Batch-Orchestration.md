# Actionable Step: Create Integration Tests for EOD Batch Orchestration

**Objective:** Create comprehensive integration tests that verify the complete EODAccrualBatch orchestration workflow including fan-out, cascade, and reconciliation.

**Prerequisites:**
- Actionable Step 5 (Create EODAccrualBatch Domain Entity and Data Model) must be completed.
- Actionable Step 6 (Implement EODAccrualBatch Workflow Criteria Functions) must be completed.
- Actionable Step 7 (Implement EODAccrualBatch Workflow Processors) must be completed.
- Actionable Step 9 (Configure EODAccrualBatch Workflow JSON) must be completed.

**Action Items:**
1. Review acceptance criteria in section 3 of cyoda-eod-accrual-workflows.md
2. Review EODAccrualBatch workflow in section 4.2 of cyoda-eod-accrual-workflows.md
3. Review testing patterns in `llm_example/` directory
4. Create `EODAccrualBatchIntegrationTest.java` in `src/test/java/com/java_template/application/integration/`
5. Add @SpringBootTest annotation for full application context
6. Inject EntityService for entity operations
7. Set up test data: create multiple sample Loan entities in ACTIVE state
8. Implement test method testBatchCreationAndValidation()
9. Create new EODAccrualBatch with state REQUESTED for current business date
10. Trigger START transition with manual=true
11. Verify IsBusinessDay criterion passes
12. Verify NoActiveBatchForDate criterion passes
13. Verify UserHasPermission criterion passes
14. Verify batch transitions to VALIDATED state
15. Implement test method testSnapshotCapture()
16. Create batch in VALIDATED state
17. Trigger TAKE_SNAPSHOT transition
18. Verify CaptureEffectiveDatedSnapshots processor executes
19. Verify ResolvePeriodStatus processor executes and sets periodStatus
20. Verify batch transitions to SNAPSHOT_TAKEN state
21. Verify principal and APR snapshots are captured for all loans
22. Implement test method testAccrualFanOut()
23. Create batch in SNAPSHOT_TAKEN state with 10 eligible loans
24. Trigger GENERATE_ACCRUALS transition
25. Verify SpawnAccrualsForEligibleLoans processor executes
26. Verify 10 Accrual entities are created (one per loan)
27. Verify each accrual has runId set to batch's batchId
28. Verify each accrual has asOfDate set to batch's asOfDate
29. Verify each accrual has priorPeriodFlag matching batch's periodStatus
30. Verify batch transitions to GENERATING state
31. Verify batch metrics show eligibleLoans = 10
32. Implement test method testAwaitPosting()
33. Create batch in GENERATING state with associated accruals
34. Simulate all accruals transitioning to POSTED state
35. Trigger AWAIT_POSTED transition
36. Verify AllAccrualsPosted criterion passes
37. Verify batch transitions to POSTING_COMPLETE state
38. Verify batch metrics show processedLoans count
39. Implement test method testTodayRunSkipsCascade()
40. Create batch for current business date (mode=TODAY)
41. Advance batch to POSTING_COMPLETE state
42. Verify IsTodayRun criterion passes
43. Verify SKIP_CASCADE_FOR_TODAY transition executes
44. Verify batch transitions directly to RECONCILING state
45. Verify no cascade operations are triggered
46. Implement test method testBackdatedRunWithCascade()
47. Create batch for past business date (mode=BACKDATED) with reasonCode
48. Advance batch to POSTING_COMPLETE state
49. Verify IsBackDatedRun criterion passes
50. Verify CASCADE_RECALC_IF_BACKDATED transition executes
51. Verify SpawnCascadeRecalc processor executes
52. Verify batch transitions to CASCADING state
53. Verify cascadeFromDate is set correctly
54. Simulate cascade operations completing
55. Trigger CASCADE_COMPLETE transition
56. Verify CascadeSettled criterion passes
57. Verify batch transitions to RECONCILING state
58. Implement test method testReconciliationAndCompletion()
59. Create batch in RECONCILING state with all accruals posted
60. Verify batch metrics show balanced debits and credits
61. Trigger FINALIZE transition
62. Verify BatchBalanced criterion passes
63. Verify ProduceReconciliationReport processor executes
64. Verify reconciliation report is generated
65. Verify reportId is set on batch
66. Verify batch transitions to COMPLETED state
67. Verify final metrics are correct (debited, credited, imbalances=0)
68. Implement test method testBatchWithImbalance()
69. Create batch with accruals that have imbalanced journal entries
70. Advance batch to RECONCILING state
71. Verify BatchBalanced criterion fails
72. Verify batch does not transition to COMPLETED
73. Verify imbalances count in metrics is greater than 0
74. Implement test method testDuplicateBatchPrevention()
75. Create and start a batch for a specific asOfDate
76. Attempt to create another batch for the same asOfDate
77. Verify NoActiveBatchForDate criterion fails
78. Verify duplicate batch is rejected
79. Implement test method testBackdatedPermissionCheck()
80. Create batch with mode=BACKDATED
81. Simulate user without backdated_eod_execute permission
82. Trigger START transition
83. Verify UserHasPermission criterion fails
84. Verify batch does not transition to VALIDATED
85. Implement test method testPartialFailureHandling()
86. Create batch with 10 eligible loans
87. Simulate 2 accruals failing during processing
88. Verify batch continues processing remaining accruals
89. Verify batch metrics reflect partial success
90. Verify failed accruals are tracked
91. Implement test method testEndToEndTodayRun()
92. Create batch for current business date
93. Execute complete workflow from REQUESTED to COMPLETED
94. Verify all state transitions occur correctly
95. Verify all accruals are created and posted
96. Verify reconciliation report is generated
97. Verify final batch state is COMPLETED
98. Implement test method testEndToEndBackdatedRun()
99. Create batch for past business date with reasonCode
100. Execute complete workflow from REQUESTED to COMPLETED
101. Verify all state transitions including CASCADING occur correctly
102. Verify priorPeriodFlag is set on accruals
103. Verify cascade recalculation executes
104. Verify reconciliation report includes PPA section
105. Verify final batch state is COMPLETED
106. Add assertions for all state transitions
107. Add assertions for all processor executions
108. Add assertions for all criterion evaluations
109. Add proper test cleanup (delete test entities after each test)
110. Run `./gradlew test` to verify all integration tests pass

**Acceptance Criteria:**
- Integration test class exists with orchestration tests
- Test covers REQUESTED → VALIDATED → SNAPSHOT_TAKEN → GENERATING → POSTING_COMPLETE → RECONCILING → COMPLETED flow
- Test covers backdated run with CASCADING state
- Test covers today run skipping cascade
- Test verifies fan-out creates correct number of accruals
- Test verifies all accruals have correct runId and asOfDate linkage
- Test verifies priorPeriodFlag propagation for backdated runs
- Test verifies cascade logic for backdated scenarios
- Test verifies reconciliation report generation
- Test verifies batch balancing validation
- Test verifies duplicate batch prevention
- Test verifies permission checking for backdated runs
- Test verifies partial failure handling
- End-to-end tests verify complete workflows
- All integration tests pass
