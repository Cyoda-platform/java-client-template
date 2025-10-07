# Actionable Step: Create New Accrual Domain Entity with Embedded Journal Entries

**Objective:** Implement the new Accrual entity with embedded JournalEntry objects following the specification's data model and inheritance contract.

**Prerequisites:** 
- Actionable Step 1 (Remove Existing Accrual, GLBatch, and GLLine Entities and Related Components) must be completed.

**Action Items:**
1. Review the Accrual data model specification in section 2.1 of cyoda-eod-accrual-workflows.md
2. Review the JournalEntry data model specification in section 2.2 of cyoda-eod-accrual-workflows.md
3. Review example entity implementations in `llm_example/code/application/entity/`
4. Create `JournalEntry.java` class in `src/main/java/com/java_template/application/entity/`
5. Add fields to JournalEntry: entryId (UUID), account (enum), direction (enum), amount (BigDecimal), kind (enum), adjustsEntryId (UUID), memo (String)
6. Create enum `JournalEntryAccount` with values: INTEREST_RECEIVABLE, INTEREST_INCOME
7. Create enum `JournalEntryDirection` with values: DR, CR
8. Create enum `JournalEntryKind` with values: ORIGINAL, REVERSAL, REPLACEMENT
9. Add validation to JournalEntry ensuring adjustsEntryId is present when kind is REVERSAL
10. Create `PrincipalSnapshot.java` class with fields: amount (BigDecimal), effectiveAtStartOfDay (boolean)
11. Create `AccrualError.java` class with fields: code (String), message (String)
12. Create enum `AccrualState` with values: NEW, ELIGIBLE, CALCULATED, POSTED, SUPERSEDED, FAILED, CANCELED
13. Create enum `DayCountConvention` with values: ACT_360, ACT_365, THIRTY_360
14. Create `Accrual.java` class in `src/main/java/com/java_template/application/entity/`
15. Implement CyodaEntity interface in Accrual class
16. Add all fields from specification section 2.1: accrualId, loanId, asOfDate, currency, aprId, dayCountConvention, dayCountFraction, principalSnapshot, interestAmount, postingTimestamp, priorPeriodFlag, runId, version, supersedesAccrualId, state, journalEntries (List<JournalEntry>), error
17. Implement getModelKey() method returning "accrual" and version "1"
18. Implement isValid() method checking required fields and business invariants
19. Add validation ensuring journalEntries list debits equal credits when state is POSTED
20. Add validation ensuring asOfDate is not null
21. Add validation ensuring loanId is not null
22. Add validation ensuring currency is valid ISO-4217 code
23. Add Jackson annotations for proper JSON serialization/deserialization
24. Add @JsonProperty annotations to map Java field names to JSON property names
25. Ensure JournalEntry does NOT contain fields inherited from parent (asOfDate, currency, loanId, postingTimestamp, priorPeriodFlag, runId)
26. Add JavaDoc comments explaining the inheritance contract for JournalEntry
27. Create unit test class `AccrualTest.java` in `src/test/java/`
28. Write test for isValid() method with valid accrual
29. Write test for isValid() method with missing required fields
30. Write test for debit/credit balance validation when state is POSTED
31. Write test for getModelKey() returning correct values
32. Run `./gradlew test` to verify all unit tests pass
33. Run `./gradlew build` to verify compilation succeeds

**Acceptance Criteria:**
- Accrual entity class exists and implements CyodaEntity interface
- JournalEntry class exists with all required fields per specification
- All required enums are created (AccrualState, DayCountConvention, JournalEntryAccount, JournalEntryDirection, JournalEntryKind)
- Inheritance contract is enforced: JournalEntry does not duplicate parent fields
- isValid() method enforces all business invariants from specification section 2.1
- Debit/credit balance validation works for POSTED state
- Unit tests pass for all validation logic
- Code compiles without errors
- JSON serialization/deserialization works correctly

