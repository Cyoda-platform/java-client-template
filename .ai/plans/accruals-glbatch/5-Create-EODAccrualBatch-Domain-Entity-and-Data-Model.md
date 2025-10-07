# Actionable Step: Create EODAccrualBatch Domain Entity and Data Model

**Objective:** Implement the EODAccrualBatch entity that orchestrates daily accounting runs following the specification's data model.

**Prerequisites:**
- Actionable Step 1 (Remove Existing Accrual, GLBatch, and GLLine Entities and Related Components) must be completed.

**Action Items:**
1. Review the EODAccrualBatch data model specification in section 2 and 13 of cyoda-eod-accrual-workflows.md
2. Review example entity implementations in `llm_example/code/application/entity/`
3. Create enum `BatchMode` with values: TODAY, BACKDATED
4. Create enum `PeriodStatus` with values: OPEN, CLOSED
5. Create enum `EODAccrualBatchState` with values: REQUESTED, VALIDATED, SNAPSHOT_TAKEN, GENERATING, POSTING_COMPLETE, CASCADING, RECONCILING, COMPLETED, FAILED, CANCELED
6. Create `LoanFilter.java` class with fields: loanIds (List<UUID>), productCodes (List<String>)
7. Create `BatchMetrics.java` class with fields: eligibleLoans (int), processedLoans (int), accrualsCreated (int), postings (int), debited (BigDecimal), credited (BigDecimal), imbalances (int)
8. Create `EODAccrualBatch.java` class in `src/main/java/com/java_template/application/entity/`
9. Implement CyodaEntity interface in EODAccrualBatch class
10. Add field batchId (UUID)
11. Add field asOfDate (LocalDate)
12. Add field mode (BatchMode enum)
13. Add field initiatedBy (String for userId)
14. Add field reasonCode (String, nullable)
15. Add field loanFilter (LoanFilter object)
16. Add field periodStatus (PeriodStatus enum)
17. Add field cascadeFromDate (LocalDate, nullable)
18. Add field metrics (BatchMetrics object)
19. Add field reportId (UUID, nullable)
20. Add field state (EODAccrualBatchState enum)
21. Implement getModelKey() method returning "eodAccrualBatch" and version "1"
22. Implement isValid() method checking required fields and business invariants
23. Add validation ensuring asOfDate is not null
24. Add validation ensuring mode is not null
25. Add validation ensuring initiatedBy is not null
26. Add validation ensuring reasonCode is present when mode is BACKDATED
27. Add validation ensuring metrics object is initialized
28. Add Jackson annotations for proper JSON serialization/deserialization
29. Add @JsonProperty annotations to map Java field names to JSON property names
30. Add JavaDoc comments explaining the orchestration purpose of this entity
31. Create unit test class `EODAccrualBatchTest.java` in `src/test/java/`
32. Write test for isValid() method with valid batch
33. Write test for isValid() method with missing required fields
34. Write test for reasonCode validation when mode is BACKDATED
35. Write test for getModelKey() returning correct values
36. Run `./gradlew test` to verify all unit tests pass
37. Run `./gradlew build` to verify compilation succeeds

**Acceptance Criteria:**
- EODAccrualBatch entity class exists and implements CyodaEntity interface
- All required enums are created (BatchMode, PeriodStatus, EODAccrualBatchState)
- LoanFilter and BatchMetrics supporting classes exist
- All fields from specification section 13 are present
- isValid() method enforces all business invariants
- Validation ensures reasonCode is required for BACKDATED mode
- Unit tests pass for all validation logic
- Code compiles without errors
- JSON serialization/deserialization works correctly

