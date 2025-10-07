# Actionable Step: Create Integration Tests for Accrual Lifecycle

**Objective:** Create comprehensive integration tests that verify the complete Accrual entity lifecycle through all workflow states.

**Prerequisites:**
- Actionable Step 2 (Create New Accrual Domain Entity with Embedded Journal Entries) must be completed.
- Actionable Step 3 (Implement Accrual Workflow Criteria Functions) must be completed.
- Actionable Step 4 (Implement Accrual Workflow Processors) must be completed.
- Actionable Step 8 (Configure Accrual Workflow JSON) must be completed.

**Action Items:**
1. Review acceptance criteria in section 3 of cyoda-eod-accrual-workflows.md
2. Review worked scenario in section 12 of cyoda-eod-accrual-workflows.md
3. Review testing patterns in `llm_example/` directory
4. Create `AccrualLifecycleIntegrationTest.java` in `src/test/java/com/java_template/application/integration/`
5. Add @SpringBootTest annotation for full application context
6. Inject EntityService for entity operations
7. Set up test data: create sample Loan entity in ACTIVE state
8. Implement test method testAccrualCreationAndValidation()
9. Create new Accrual with state NEW for the sample loan
10. Verify accrual transitions to ELIGIBLE state automatically
11. Verify IsBusinessDay criterion passes
12. Verify LoanActiveOnDate criterion passes
13. Verify NotDuplicateAccrual criterion passes
14. Verify principalSnapshot.amount > 0 criterion passes
15. Implement test method testAccrualCalculation()
16. Create accrual in ELIGIBLE state
17. Trigger CALCULATE transition
18. Verify DeriveDayCountFraction processor executes and sets dayCountFraction
19. Verify CalculateAccrualAmount processor executes and sets interestAmount
20. Verify accrual transitions to CALCULATED state
21. Verify interestAmount = principal × APR × dayCountFraction
22. Implement test method testAccrualPosting()
23. Create accrual in CALCULATED state
24. Trigger WRITE_JOURNALS transition
25. Verify SubledgerAvailable criterion passes
26. Verify WriteAccrualJournalEntries processor creates two journal entries
27. Verify DR entry for INTEREST_RECEIVABLE with correct amount
28. Verify CR entry for INTEREST_INCOME with correct amount
29. Verify both entries have kind=ORIGINAL
30. Verify UpdateLoanAccruedInterest processor updates loan balance
31. Verify accrual transitions to POSTED state
32. Verify sum of debits equals sum of credits
33. Implement test method testAccrualRebook()
34. Create initial accrual in POSTED state with $100 DR/CR entries
35. Modify underlying loan data to trigger rebook requirement
36. Trigger SUPERSEDE_AND_REBOOK transition
37. Verify RequiresRebook criterion passes
38. Verify ReversePriorJournals processor creates REVERSAL entries
39. Verify REVERSAL entries reference original entryIds via adjustsEntryId
40. Verify CreateReplacementAccrual processor creates new accrual
41. Verify new accrual has REPLACEMENT entries with corrected amounts
42. Verify new accrual sets supersedesAccrualId to original accrual's ID
43. Verify original accrual transitions to SUPERSEDED state
44. Verify net effect on loan balance is correct (delta between original and replacement)
45. Implement test method testBackdatedAccrualWithPPA()
46. Create accrual with asOfDate in a closed GL period
47. Verify priorPeriodFlag is set to true
48. Verify accrual processes through all states correctly
49. Verify journal entries inherit priorPeriodFlag from parent accrual
50. Implement test method testDuplicateAccrualPrevention()
51. Create and post an accrual for (loanId, asOfDate)
52. Attempt to create another accrual for same (loanId, asOfDate)
53. Verify NotDuplicateAccrual criterion fails
54. Verify duplicate accrual is rejected
55. Implement test method testAccrualFailureHandling()
56. Create accrual with invalid data (e.g., principal = 0)
57. Verify accrual does not transition past NEW state
58. Verify error information is captured in accrual.error field
59. Implement test method testManualTransitions()
60. Create accrual in ELIGIBLE state
61. Trigger manual REJECT transition
62. Verify accrual transitions to FAILED state
63. Create another accrual in CALCULATED state
64. Trigger manual CANCEL transition
65. Verify accrual transitions to CANCELED state
66. Add assertions for all state transitions
67. Add assertions for all processor executions
68. Add assertions for all criterion evaluations
69. Add proper test cleanup (delete test entities after each test)
70. Run `./gradlew test` to verify all integration tests pass

**Acceptance Criteria:**
- Integration test class exists with comprehensive lifecycle tests
- Test covers NEW → ELIGIBLE → CALCULATED → POSTED state flow
- Test covers POSTED → SUPERSEDED flow for rebook scenario
- Test verifies all processors execute correctly
- Test verifies all criteria evaluate correctly
- Test verifies journal entry creation with correct DR/CR amounts
- Test verifies loan balance updates correctly
- Test verifies reversal and replacement entry linking via adjustsEntryId
- Test verifies prior period flag handling for backdated accruals
- Test verifies duplicate prevention logic
- Test verifies manual transition handling (REJECT, CANCEL)
- All integration tests pass
- Tests follow the worked scenario from section 12 of specification

