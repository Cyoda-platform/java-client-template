# Actionable Step: Configure Accrual Workflow JSON

**Objective:** Create the Accrual workflow configuration JSON file following the specification in section 4.1.

**Prerequisites:**
- Actionable Step 3 (Implement Accrual Workflow Criteria Functions) must be completed.
- Actionable Step 4 (Implement Accrual Workflow Processors) must be completed.

**Action Items:**
1. Review Accrual workflow specification in section 4.1 of cyoda-eod-accrual-workflows.md
2. Review workflow JSON templates in `llm_example/config/workflow/`
3. Identify the workflow configuration directory (likely `src/main/resources/workflow/` or `application/resources/workflow/`)
4. Create file `accrual-workflow.json` in the workflow configuration directory
5. Set workflow version to "1.0"
6. Set workflow name to "Accrual Workflow"
7. Set initialState to "NEW"
8. Define NEW state with VALIDATE transition
9. Configure VALIDATE transition with next state "ELIGIBLE" and manual=false
10. Add criterion group with AND operator for VALIDATE transition
11. Add IsBusinessDay function criterion with attachEntity=true
12. Add LoanActiveOnDate function criterion with attachEntity=true
13. Add NotDuplicateAccrual function criterion with attachEntity=true
14. Add simple criterion for $.principalSnapshot.amount GREATER_THAN 0
15. Define ELIGIBLE state with CALCULATE and REJECT transitions
16. Configure CALCULATE transition with next state "CALCULATED" and manual=false
17. Add DeriveDayCountFraction processor with SYNC execution mode and attachEntity=true
18. Add CalculateAccrualAmount processor with ASYNC_NEW_TX execution mode, attachEntity=true, and calculationNodesTags="accruals"
19. Configure REJECT transition with next state "FAILED" and manual=true
20. Define CALCULATED state with WRITE_JOURNALS and CANCEL transitions
21. Configure WRITE_JOURNALS transition with next state "POSTED" and manual=false
22. Add SubledgerAvailable function criterion for WRITE_JOURNALS transition
23. Add WriteAccrualJournalEntries processor with ASYNC_NEW_TX execution mode, attachEntity=true, and targetPath="$.journalEntries"
24. Add UpdateLoanAccruedInterest processor with ASYNC_NEW_TX execution mode, attachEntity=true, and sourcePath="$.journalEntries"
25. Configure CANCEL transition with next state "CANCELED" and manual=true
26. Define POSTED state with SUPERSEDE_AND_REBOOK transition
27. Configure SUPERSEDE_AND_REBOOK transition with next state "SUPERSEDED" and manual=false
28. Add RequiresRebook function criterion for SUPERSEDE_AND_REBOOK transition
29. Add ReversePriorJournals processor with ASYNC_NEW_TX execution mode, attachEntity=true, and targetPath="$.journalEntries"
30. Add CreateReplacementAccrual processor with ASYNC_NEW_TX execution mode, attachEntity=true, and calculationNodesTags="accruals"
31. Define SUPERSEDED state with empty transitions array
32. Define FAILED state with empty transitions array
33. Define CANCELED state with empty transitions array
34. Validate JSON syntax is correct
35. Ensure all processor names match the supports() method return values
36. Ensure all criterion function names match the supports() method return values
37. Ensure all state names match the AccrualState enum values
38. Review workflow for completeness against specification section 4.1
39. Run `./gradlew build` to verify the workflow file is loaded correctly
40. Use WorkflowImportTool or similar to validate workflow configuration if available

**Acceptance Criteria:**
- accrual-workflow.json file exists in the correct workflow configuration directory
- JSON is syntactically valid
- All states from specification are defined: NEW, ELIGIBLE, CALCULATED, POSTED, SUPERSEDED, FAILED, CANCELED
- All transitions match specification section 4.1
- All processor names match implemented processor supports() values
- All criterion names match implemented criterion supports() values
- Processor execution modes and configurations match specification
- Workflow file loads without errors during application startup
- Workflow configuration can be imported successfully

