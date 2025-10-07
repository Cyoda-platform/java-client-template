# Actionable Step: Configure EODAccrualBatch Workflow JSON

**Objective:** Create the EODAccrualBatch workflow configuration JSON file following the specification in section 4.2.

**Prerequisites:**
- Actionable Step 6 (Implement EODAccrualBatch Workflow Criteria Functions) must be completed.
- Actionable Step 7 (Implement EODAccrualBatch Workflow Processors) must be completed.

**Action Items:**
1. Review EODAccrualBatch workflow specification in section 4.2 of cyoda-eod-accrual-workflows.md
2. Review workflow JSON templates in `llm_example/config/workflow/`
3. Identify the workflow configuration directory (likely `src/main/resources/workflow/` or `application/resources/workflow/`)
4. Create file `eod-accrual-batch-workflow.json` in the workflow configuration directory
5. Set workflow version to "1.0"
6. Set workflow name to "EOD Accrual Batch"
7. Set initialState to "REQUESTED"
8. Define REQUESTED state with START transition
9. Configure START transition with next state "VALIDATED" and manual=true
10. Add criterion group with AND operator for START transition
11. Add IsBusinessDay function criterion with attachEntity=true
12. Add NoActiveBatchForDate function criterion with attachEntity=true
13. Add UserHasPermission function criterion with attachEntity=true and context="backdated_eod_execute"
14. Define VALIDATED state with TAKE_SNAPSHOT transition
15. Configure TAKE_SNAPSHOT transition with next state "SNAPSHOT_TAKEN" and manual=false
16. Add CaptureEffectiveDatedSnapshots processor with ASYNC_NEW_TX execution mode, attachEntity=true, and calculationNodesTags="accruals"
17. Add ResolvePeriodStatus processor with SYNC execution mode and attachEntity=true
18. Define SNAPSHOT_TAKEN state with GENERATE_ACCRUALS transition
19. Configure GENERATE_ACCRUALS transition with next state "GENERATING" and manual=false
20. Add SpawnAccrualsForEligibleLoans processor with ASYNC_NEW_TX execution mode, attachEntity=true, and calculationNodesTags="accruals"
21. Define GENERATING state with AWAIT_POSTED transition
22. Configure AWAIT_POSTED transition with next state "POSTING_COMPLETE" and manual=false
23. Add AllAccrualsPosted function criterion for AWAIT_POSTED transition
24. Define POSTING_COMPLETE state with CASCADE_RECALC_IF_BACKDATED and SKIP_CASCADE_FOR_TODAY transitions
25. Configure CASCADE_RECALC_IF_BACKDATED transition with next state "CASCADING" and manual=false
26. Add IsBackDatedRun function criterion for CASCADE_RECALC_IF_BACKDATED transition
27. Add SpawnCascadeRecalc processor with ASYNC_NEW_TX execution mode, attachEntity=true, and calculationNodesTags="recalc"
28. Configure SKIP_CASCADE_FOR_TODAY transition with next state "RECONCILING" and manual=false
29. Add IsTodayRun function criterion for SKIP_CASCADE_FOR_TODAY transition
30. Define CASCADING state with CASCADE_COMPLETE transition
31. Configure CASCADE_COMPLETE transition with next state "RECONCILING" and manual=false
32. Add CascadeSettled function criterion for CASCADE_COMPLETE transition
33. Define RECONCILING state with FINALIZE transition
34. Configure FINALIZE transition with next state "COMPLETED" and manual=false
35. Add BatchBalanced function criterion for FINALIZE transition
36. Add ProduceReconciliationReport processor with ASYNC_NEW_TX execution mode, attachEntity=true, and calculationNodesTags="ledger"
37. Define COMPLETED state with empty transitions array
38. Define FAILED state with empty transitions array
39. Define CANCELED state with empty transitions array
40. Validate JSON syntax is correct
41. Ensure all processor names match the supports() method return values
42. Ensure all criterion function names match the supports() method return values
43. Ensure all state names match the EODAccrualBatchState enum values
44. Review workflow for completeness against specification section 4.2
45. Run `./gradlew build` to verify the workflow file is loaded correctly
46. Use WorkflowImportTool or similar to validate workflow configuration if available

**Acceptance Criteria:**
- eod-accrual-batch-workflow.json file exists in the correct workflow configuration directory
- JSON is syntactically valid
- All states from specification are defined: REQUESTED, VALIDATED, SNAPSHOT_TAKEN, GENERATING, POSTING_COMPLETE, CASCADING, RECONCILING, COMPLETED, FAILED, CANCELED
- All transitions match specification section 4.2
- All processor names match implemented processor supports() values
- All criterion names match implemented criterion supports() values
- Processor execution modes and configurations match specification
- Branching logic for backdated vs today runs is correctly configured
- Workflow file loads without errors during application startup
- Workflow configuration can be imported successfully

