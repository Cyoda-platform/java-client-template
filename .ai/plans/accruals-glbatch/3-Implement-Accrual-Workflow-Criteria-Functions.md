# Actionable Step: Implement Accrual Workflow Criteria Functions

**Objective:** Implement all criteria functions required by the Accrual workflow as defined in specification sections 4.1 and 6.

**Prerequisites:**
- Actionable Step 2 (Create New Accrual Domain Entity with Embedded Journal Entries) must be completed.

**Action Items:**
1. Review criteria specifications in section 6 of cyoda-eod-accrual-workflows.md
2. Review Accrual workflow configuration in section 4.1 of cyoda-eod-accrual-workflows.md
3. Review example criterion implementations in `llm_example/code/application/criterion/`
4. Review CyodaCriterion interface in `src/main/java/com/java_template/common/workflow/`
5. Create `IsBusinessDayCriterion.java` implementing CyodaCriterion in `src/main/java/com/java_template/application/criterion/`
6. Implement check() method to validate AsOfDate against business calendar
7. Implement supports() method returning "IsBusinessDay"
8. Create `LoanActiveOnDateCriterion.java` implementing CyodaCriterion
9. Implement check() method to verify loan was ACTIVE on AsOfDate, handling NON_ACCRUAL and charge-off policies
10. Implement supports() method returning "LoanActiveOnDate"
11. Create `NotDuplicateAccrualCriterion.java` implementing CyodaCriterion
12. Implement check() method to prevent duplicate accruals for (loanId, asOfDate) unless superseding
13. Implement supports() method returning "NotDuplicateAccrual"
14. Use EntityService to query existing accruals by loanId and asOfDate
15. Create `SubledgerAvailableCriterion.java` implementing CyodaCriterion
16. Implement check() method to verify sub-ledger is reachable and accounts are configured
17. Implement supports() method returning "SubledgerAvailable"
18. Create `RequiresRebookCriterion.java` implementing CyodaCriterion
19. Implement check() method to determine if underlying data change yields non-zero delta for same AsOfDate
20. Implement supports() method returning "RequiresRebook"
21. Add CriterionSerializer usage for type-safe entity processing in all criteria
22. Ensure all criteria are pure functions with no side effects
23. Ensure all criteria do NOT modify entities
24. Add proper error handling and logging in all criteria
25. Create unit test class `IsBusinessDayCriterionTest.java`
26. Write tests for valid and invalid business days
27. Create unit test class `LoanActiveOnDateCriterionTest.java`
28. Write tests for active loans, inactive loans, and NON_ACCRUAL scenarios
29. Create unit test class `NotDuplicateAccrualCriterionTest.java`
30. Write tests for duplicate detection and superseding scenarios
31. Create unit test class `SubledgerAvailableCriterionTest.java`
32. Write tests for available and unavailable sub-ledger scenarios
33. Create unit test class `RequiresRebookCriterionTest.java`
34. Write tests for rebook required and not required scenarios
35. Run `./gradlew test` to verify all criterion tests pass
36. Run `./gradlew build` to verify compilation succeeds

**Acceptance Criteria:**
- All five Accrual workflow criteria are implemented: IsBusinessDay, LoanActiveOnDate, NotDuplicateAccrual, SubledgerAvailable, RequiresRebook
- Each criterion implements CyodaCriterion interface correctly
- Each criterion's supports() method returns the correct name matching workflow configuration
- All criteria are pure functions with no side effects
- CriterionSerializer is used for type-safe processing
- Unit tests exist and pass for all criteria
- Code compiles without errors
- All criteria integrate with EntityService for data queries where needed

