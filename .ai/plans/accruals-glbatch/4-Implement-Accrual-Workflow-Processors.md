# Actionable Step: Implement Accrual Workflow Processors

**Objective:** Implement all processor functions required by the Accrual workflow as defined in specification sections 4.1 and 5.

**Prerequisites:**
- Actionable Step 2 (Create New Accrual Domain Entity with Embedded Journal Entries) must be completed.
- Actionable Step 3 (Implement Accrual Workflow Criteria Functions) must be completed.

**Action Items:**
1. Review processor specifications in section 5 of cyoda-eod-accrual-workflows.md
2. Review Accrual workflow configuration in section 4.1 of cyoda-eod-accrual-workflows.md
3. Review example processor implementations in `llm_example/code/application/processor/`
4. Review CyodaProcessor interface in `src/main/java/com/java_template/common/workflow/`
5. Create `DeriveDayCountFractionProcessor.java` implementing CyodaProcessor in `src/main/java/com/java_template/application/processor/`
6. Implement process() method to compute day-count fraction per product convention (ACT_360, ACT_365, THIRTY_360)
7. Implement supports() method returning "DeriveDayCountFraction"
8. Use ProcessorSerializer for type-safe entity processing
9. Create `CalculateAccrualAmountProcessor.java` implementing CyodaProcessor
10. Implement process() method with formula: interestAmount = principal × APR × DayCountFraction
11. Implement supports() method returning "CalculateAccrualAmount"
12. Mark as ASYNC_NEW_TX execution mode compatible
13. Create `WriteAccrualJournalEntriesProcessor.java` implementing CyodaProcessor
14. Implement process() method to write embedded DR/CR entries to $.journalEntries
15. Create DR entry for INTEREST_RECEIVABLE with interestAmount
16. Create CR entry for INTEREST_INCOME with interestAmount
17. Set kind to ORIGINAL for new entries
18. Generate unique entryId for each journal entry
19. Implement supports() method returning "WriteAccrualJournalEntries"
20. Create `UpdateLoanAccruedInterestProcessor.java` implementing CyodaProcessor
21. Implement process() method to update loan's accruedInterest from net delta of entries
22. Use EntityService to read current loan entity
23. Calculate net delta from journalEntries (considering REVERSAL and REPLACEMENT kinds)
24. Update loan entity with new accruedInterest balance
25. Use EntityService to update the loan entity (NOT the current accrual entity)
26. Implement supports() method returning "UpdateLoanAccruedInterest"
27. Create `ReversePriorJournalsProcessor.java` implementing CyodaProcessor
28. Implement process() method to append equal-and-opposite REVERSAL entries
29. Query EntityService for the prior accrual using supersedesAccrualId
30. For each ORIGINAL entry in prior accrual, create REVERSAL entry with opposite direction
31. Set adjustsEntryId to reference the original entryId
32. Implement supports() method returning "ReversePriorJournals"
33. Create `CreateReplacementAccrualProcessor.java` implementing CyodaProcessor
34. Implement process() method to create new Accrual for same asOfDate with REPLACEMENT entries
35. Set supersedesAccrualId to the current accrual's accrualId
36. Create REPLACEMENT entries with corrected amounts
37. Use EntityService to create the new accrual entity
38. Implement supports() method returning "CreateReplacementAccrual"
39. Ensure all processors use EntityService to read current entity
40. Ensure processors can update OTHER entities but NOT the current accrual with EntityService
41. Add proper error handling and logging in all processors
42. Create unit test class `DeriveDayCountFractionProcessorTest.java`
43. Write tests for ACT_360, ACT_365, and THIRTY_360 conventions
44. Create unit test class `CalculateAccrualAmountProcessorTest.java`
45. Write tests for interest calculation with various principal and APR values
46. Create unit test class `WriteAccrualJournalEntriesProcessorTest.java`
47. Write tests verifying DR/CR entries are created correctly
48. Create unit test class `UpdateLoanAccruedInterestProcessorTest.java`
49. Write tests for loan balance updates with mocked EntityService
50. Create unit test class `ReversePriorJournalsProcessorTest.java`
51. Write tests for reversal entry creation
52. Create unit test class `CreateReplacementAccrualProcessorTest.java`
53. Write tests for replacement accrual creation
54. Run `./gradlew test` to verify all processor tests pass
55. Run `./gradlew build` to verify compilation succeeds

**Acceptance Criteria:**
- All six Accrual workflow processors are implemented: DeriveDayCountFraction, CalculateAccrualAmount, WriteAccrualJournalEntries, UpdateLoanAccruedInterest, ReversePriorJournals, CreateReplacementAccrual
- Each processor implements CyodaProcessor interface correctly
- Each processor's supports() method returns the correct name matching workflow configuration
- ProcessorSerializer is used for type-safe processing
- Processors correctly use EntityService to read current entity and update OTHER entities
- Journal entry creation follows the embedded model with proper inheritance contract
- Reversal and replacement logic correctly links entries via adjustsEntryId
- Unit tests exist and pass for all processors
- Code compiles without errors

