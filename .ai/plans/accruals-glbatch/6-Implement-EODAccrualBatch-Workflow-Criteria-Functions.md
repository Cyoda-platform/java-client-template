# Actionable Step: Implement EODAccrualBatch Workflow Criteria Functions

**Objective:** Implement all criteria functions required by the EODAccrualBatch workflow as defined in specification sections 4.2 and 6.

**Prerequisites:**
- Actionable Step 5 (Create EODAccrualBatch Domain Entity and Data Model) must be completed.

**Action Items:**
1. Review criteria specifications in section 6 of cyoda-eod-accrual-workflows.md
2. Review EODAccrualBatch workflow configuration in section 4.2 of cyoda-eod-accrual-workflows.md
3. Review example criterion implementations in `llm_example/code/application/criterion/`
4. Reuse `IsBusinessDayCriterion.java` from Accrual workflow (already implemented in Step 3)
5. Create `NoActiveBatchForDateCriterion.java` implementing CyodaCriterion in `src/main/java/com/java_template/application/criterion/`
6. Implement check() method to ensure only one active batch per AsOfDate
7. Use EntityService to query existing batches by asOfDate
8. Check that no batch exists in non-terminal states (not COMPLETED, FAILED, or CANCELED)
9. Implement supports() method returning "NoActiveBatchForDate"
10. Create `UserHasPermissionCriterion.java` implementing CyodaCriterion
11. Implement check() method to verify user has required permission from config
12. Extract permission name from config (e.g., "backdated_eod_execute")
13. Validate user permissions against the required permission
14. Implement supports() method returning "UserHasPermission"
15. Create `AllAccrualsPostedCriterion.java` implementing CyodaCriterion
16. Implement check() method to verify all fan-out Accruals are POSTED or terminal
17. Use EntityService to query all accruals by runId (batch's batchId)
18. Check that all accruals are in POSTED, FAILED, CANCELED, or SUPERSEDED states
19. Implement supports() method returning "AllAccrualsPosted"
20. Create `IsBackDatedRunCriterion.java` implementing CyodaCriterion
21. Implement check() method to determine if asOfDate is before current business date
22. Implement supports() method returning "IsBackDatedRun"
23. Create `IsTodayRunCriterion.java` implementing CyodaCriterion
24. Implement check() method to determine if asOfDate equals current business date
25. Implement supports() method returning "IsTodayRun"
26. Create `CascadeSettledCriterion.java` implementing CyodaCriterion
27. Implement check() method to verify all cascade recomputations are finished
28. Query for any pending cascade operations related to this batch
29. Implement supports() method returning "CascadeSettled"
30. Create `BatchBalancedCriterion.java` implementing CyodaCriterion
31. Implement check() method to verify debits equal credits with no unsettled items
32. Use batch metrics to compare debited and credited amounts
33. Ensure imbalances count is zero
34. Implement supports() method returning "BatchBalanced"
35. Add CriterionSerializer usage for type-safe entity processing in all criteria
36. Ensure all criteria are pure functions with no side effects
37. Ensure all criteria do NOT modify entities
38. Add proper error handling and logging in all criteria
39. Create unit test class `NoActiveBatchForDateCriterionTest.java`
40. Write tests for scenarios with and without active batches
41. Create unit test class `UserHasPermissionCriterionTest.java`
42. Write tests for users with and without required permissions
43. Create unit test class `AllAccrualsPostedCriterionTest.java`
44. Write tests for all posted, partially posted, and failed scenarios
45. Create unit test class `IsBackDatedRunCriterionTest.java`
46. Write tests for backdated and current date scenarios
47. Create unit test class `IsTodayRunCriterionTest.java`
48. Write tests for today and non-today scenarios
49. Create unit test class `CascadeSettledCriterionTest.java`
50. Write tests for settled and pending cascade scenarios
51. Create unit test class `BatchBalancedCriterionTest.java`
52. Write tests for balanced and imbalanced batch scenarios
53. Run `./gradlew test` to verify all criterion tests pass
54. Run `./gradlew build` to verify compilation succeeds

**Acceptance Criteria:**
- All seven EODAccrualBatch workflow criteria are implemented: IsBusinessDay (reused), NoActiveBatchForDate, UserHasPermission, AllAccrualsPosted, IsBackDatedRun, IsTodayRun, CascadeSettled, BatchBalanced
- Each criterion implements CyodaCriterion interface correctly
- Each criterion's supports() method returns the correct name matching workflow configuration
- All criteria are pure functions with no side effects
- CriterionSerializer is used for type-safe processing
- Unit tests exist and pass for all criteria
- Code compiles without errors
- All criteria integrate with EntityService for data queries where needed

