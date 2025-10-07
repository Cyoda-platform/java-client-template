# Actionable Step: Implement GL Aggregation and Reporting Logic

**Objective:** Implement the GL aggregation logic for end-of-month processing as defined in section 8.

**Prerequisites:**
- Actionable Step 2 (Create New Accrual Domain Entity with Embedded Journal Entries) must be completed.
- Actionable Step 4 (Implement Accrual Workflow Processors) must be completed.

**Action Items:**
1. Review GL aggregation specification in section 8 of cyoda-eod-accrual-workflows.md
2. Create `GLAggregationKey.java` class with fields: asOfDate (LocalDate), account (JournalEntryAccount), direction (JournalEntryDirection), currency (String), priorPeriodFlag (boolean)
3. Implement equals() and hashCode() methods for GLAggregationKey
4. Create `GLAggregationEntry.java` class with fields: key (GLAggregationKey), totalAmount (BigDecimal), entryCount (int)
5. Create `GLMonthlyReport.java` class with fields: month (YearMonth), entries (List<GLAggregationEntry>), totalDebits (BigDecimal), totalCredits (BigDecimal), priorPeriodAdjustments (List<GLAggregationEntry>), batchFileId (String), checksum (String)
6. Create `GLAggregationService.java` in `src/main/java/com/java_template/application/service/`
7. Add @Service annotation
8. Inject EntityService dependency via constructor
9. Implement method aggregateMonthlyJournals(YearMonth month) returning GLMonthlyReport
10. Query all Accrual entities where asOfDate falls within the specified month
11. Iterate through each accrual's journalEntries list
12. For each journal entry, extract inherited fields from parent Accrual: asOfDate, currency, priorPeriodFlag
13. Extract entry-specific fields: account, direction, amount
14. Create GLAggregationKey from (asOfDate, account, direction, currency, priorPeriodFlag)
15. Group journal entries by GLAggregationKey
16. Sum amounts for each group
17. Separate entries with priorPeriodFlag=true into priorPeriodAdjustments list
18. Calculate totalDebits (sum of all DR amounts)
19. Calculate totalCredits (sum of all CR amounts)
20. Generate batchFileId (UUID or timestamp-based identifier)
21. Calculate checksum of the report data for integrity verification
22. Return GLMonthlyReport with all aggregated data
23. Implement method exportReportToCSV(GLMonthlyReport report, Path outputPath)
24. Create CSV header: AsOfDate, Account, Direction, Currency, PriorPeriodFlag, Amount, EntryCount
25. Write each GLAggregationEntry to CSV row
26. Include separate section for prior period adjustments
27. Include summary row with total debits and credits
28. Write batch file ID and checksum to CSV footer or metadata
29. Implement method exportReportToJSON(GLMonthlyReport report, Path outputPath)
30. Serialize GLMonthlyReport to JSON format
31. Write JSON to specified output path
32. Implement method validateReportBalance(GLMonthlyReport report) returning boolean
33. Check that totalDebits equals totalCredits
34. Return true if balanced, false otherwise
35. Add proper error handling for file I/O operations
36. Add logging for aggregation process (start, progress, completion)
37. Create unit test class `GLAggregationServiceTest.java`
38. Write test for aggregateMonthlyJournals with sample accruals
39. Write test verifying correct grouping by aggregation key
40. Write test verifying inherited fields are correctly resolved from parent Accrual
41. Write test for prior period adjustment separation
42. Write test for debit/credit totals calculation
43. Write test for exportReportToCSV
44. Write test for exportReportToJSON
45. Write test for validateReportBalance with balanced report
46. Write test for validateReportBalance with imbalanced report
47. Create integration test class `GLAggregationIntegrationTest.java`
48. Create sample accruals with various journal entries
49. Test end-to-end aggregation and export
50. Verify CSV and JSON output files are created correctly
51. Run `./gradlew test` to verify all tests pass
52. Run `./gradlew build` to verify compilation succeeds

**Acceptance Criteria:**
- GLAggregationService exists with aggregation logic
- Aggregation correctly groups by (asOfDate, account, direction, currency, priorPeriodFlag)
- Journal entry fields are correctly inherited from parent Accrual
- Prior period adjustments are separated into designated section
- Total debits and credits are calculated correctly
- CSV export functionality works and produces valid CSV files
- JSON export functionality works and produces valid JSON files
- Balance validation correctly identifies balanced and imbalanced reports
- Unit tests exist and pass for all aggregation logic
- Integration tests verify end-to-end functionality
- Code compiles without errors

