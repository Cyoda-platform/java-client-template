package com.java_template.application.service.gl;

import com.java_template.application.entity.accrual.version_1.*;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GLAggregationService.
 */
class GLAggregationServiceTest {

    @Mock
    private EntityService entityService;

    private GLAggregationService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GLAggregationService(entityService);
    }

    @Test
    @DisplayName("Should aggregate monthly journals correctly")
    void testAggregateMonthlyJournals() throws Exception {
        // Given - Sample accruals for August 2025
        YearMonth month = YearMonth.of(2025, 8);
        List<EntityWithMetadata<Accrual>> accruals = createSampleAccruals();

        when(entityService.findAll(any(ModelSpec.class), eq(Accrual.class)))
                .thenReturn(accruals);

        // When
        GLMonthlyReport report = service.aggregateMonthlyJournals(month);

        // Then
        assertNotNull(report);
        assertEquals(month, report.getMonth());
        assertNotNull(report.getEntries());
        assertNotNull(report.getBatchFileId());
        assertNotNull(report.getChecksum());

        // Verify totals are calculated
        assertTrue(report.getTotalDebits().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(report.getTotalCredits().compareTo(BigDecimal.ZERO) > 0);

        // Verify report is balanced
        assertTrue(report.isBalanced());
    }

    @Test
    @DisplayName("Should correctly group entries by aggregation key")
    void testGroupingByAggregationKey() throws Exception {
        // Given - Two accruals with same date, account, direction, currency
        YearMonth month = YearMonth.of(2025, 8);
        LocalDate asOfDate = LocalDate.of(2025, 8, 15);

        List<EntityWithMetadata<Accrual>> accruals = new ArrayList<>();

        // First accrual with $100 interest
        Accrual accrual1 = createAccrual("ACC-001", "LOAN-001", asOfDate, "USD", new BigDecimal("100.00"), false);
        accruals.add(new EntityWithMetadata<>(accrual1, createMetadata()));

        // Second accrual with $50 interest (same date, same loan type)
        Accrual accrual2 = createAccrual("ACC-002", "LOAN-002", asOfDate, "USD", new BigDecimal("50.00"), false);
        accruals.add(new EntityWithMetadata<>(accrual2, createMetadata()));

        when(entityService.findAll(any(ModelSpec.class), eq(Accrual.class)))
                .thenReturn(accruals);

        // When
        GLMonthlyReport report = service.aggregateMonthlyJournals(month);

        // Then - Should have 2 aggregation entries (1 for DR, 1 for CR)
        assertEquals(2, report.getEntries().size());

        // Find the DR entry
        GLAggregationEntry drEntry = report.getEntries().stream()
                .filter(e -> e.getKey().getDirection() == JournalEntryDirection.DR)
                .findFirst()
                .orElseThrow();

        // Should aggregate both accruals: $100 + $50 = $150
        assertEquals(new BigDecimal("150.00"), drEntry.getTotalAmount());
        assertEquals(2, drEntry.getEntryCount());
    }

    @Test
    @DisplayName("Should correctly inherit fields from parent Accrual")
    void testInheritedFieldsFromParentAccrual() throws Exception {
        // Given - Accrual with specific inherited fields
        YearMonth month = YearMonth.of(2025, 8);
        LocalDate asOfDate = LocalDate.of(2025, 8, 15);
        String currency = "EUR";
        boolean priorPeriodFlag = true;

        Accrual accrual = createAccrual("ACC-001", "LOAN-001", asOfDate, currency,
                new BigDecimal("100.00"), priorPeriodFlag);

        List<EntityWithMetadata<Accrual>> accruals = List.of(
                new EntityWithMetadata<>(accrual, createMetadata())
        );

        when(entityService.findAll(any(ModelSpec.class), eq(Accrual.class)))
                .thenReturn(accruals);

        // When
        GLMonthlyReport report = service.aggregateMonthlyJournals(month);

        // Then - Verify inherited fields are correctly resolved
        for (GLAggregationEntry entry : report.getEntries()) {
            assertEquals(asOfDate, entry.getKey().getAsOfDate());
            assertEquals(currency, entry.getKey().getCurrency());
            assertEquals(priorPeriodFlag, entry.getKey().isPriorPeriodFlag());
        }
    }

    @Test
    @DisplayName("Should separate prior period adjustments")
    void testPriorPeriodAdjustmentSeparation() throws Exception {
        // Given - Mix of regular and PPA accruals
        YearMonth month = YearMonth.of(2025, 8);
        List<EntityWithMetadata<Accrual>> accruals = new ArrayList<>();

        // Regular accrual
        Accrual regularAccrual = createAccrual("ACC-001", "LOAN-001",
                LocalDate.of(2025, 8, 15), "USD", new BigDecimal("100.00"), false);
        accruals.add(new EntityWithMetadata<>(regularAccrual, createMetadata()));

        // Prior period adjustment
        Accrual ppaAccrual = createAccrual("ACC-002", "LOAN-002",
                LocalDate.of(2025, 8, 16), "USD", new BigDecimal("50.00"), true);
        accruals.add(new EntityWithMetadata<>(ppaAccrual, createMetadata()));

        when(entityService.findAll(any(ModelSpec.class), eq(Accrual.class)))
                .thenReturn(accruals);

        // When
        GLMonthlyReport report = service.aggregateMonthlyJournals(month);

        // Then - Should have 2 PPA entries (DR and CR)
        assertEquals(2, report.getPriorPeriodAdjustments().size());

        // All PPA entries should have priorPeriodFlag=true
        for (GLAggregationEntry entry : report.getPriorPeriodAdjustments()) {
            assertTrue(entry.getKey().isPriorPeriodFlag());
        }
    }

    @Test
    @DisplayName("Should calculate debit and credit totals correctly")
    void testDebitCreditTotalsCalculation() throws Exception {
        // Given - Accruals with known amounts
        YearMonth month = YearMonth.of(2025, 8);
        List<EntityWithMetadata<Accrual>> accruals = new ArrayList<>();

        // $100 accrual
        accruals.add(new EntityWithMetadata<>(
                createAccrual("ACC-001", "LOAN-001", LocalDate.of(2025, 8, 15),
                        "USD", new BigDecimal("100.00"), false),
                createMetadata()
        ));

        // $200 accrual
        accruals.add(new EntityWithMetadata<>(
                createAccrual("ACC-002", "LOAN-002", LocalDate.of(2025, 8, 16),
                        "USD", new BigDecimal("200.00"), false),
                createMetadata()
        ));

        when(entityService.findAll(any(ModelSpec.class), eq(Accrual.class)))
                .thenReturn(accruals);

        // When
        GLMonthlyReport report = service.aggregateMonthlyJournals(month);

        // Then - Total debits and credits should both be $300
        assertEquals(new BigDecimal("300.00"), report.getTotalDebits());
        assertEquals(new BigDecimal("300.00"), report.getTotalCredits());
        assertTrue(report.isBalanced());
    }

    @Test
    @DisplayName("Should export report to CSV successfully")
    void testExportReportToCSV() throws Exception {
        // Given
        YearMonth month = YearMonth.of(2025, 8);
        List<EntityWithMetadata<Accrual>> accruals = createSampleAccruals();

        when(entityService.findAll(any(ModelSpec.class), eq(Accrual.class)))
                .thenReturn(accruals);

        GLMonthlyReport report = service.aggregateMonthlyJournals(month);
        Path csvPath = tempDir.resolve("gl-report.csv");

        // When
        service.exportReportToCSV(report, csvPath);

        // Then
        assertTrue(Files.exists(csvPath));
        List<String> lines = Files.readAllLines(csvPath);
        assertTrue(lines.size() > 0);

        // Verify header
        assertTrue(lines.get(0).contains("AsOfDate"));
        assertTrue(lines.get(0).contains("Account"));
        assertTrue(lines.get(0).contains("Direction"));
    }

    @Test
    @DisplayName("Should export report to JSON successfully")
    void testExportReportToJSON() throws Exception {
        // Given
        YearMonth month = YearMonth.of(2025, 8);
        List<EntityWithMetadata<Accrual>> accruals = createSampleAccruals();

        when(entityService.findAll(any(ModelSpec.class), eq(Accrual.class)))
                .thenReturn(accruals);

        GLMonthlyReport report = service.aggregateMonthlyJournals(month);
        Path jsonPath = tempDir.resolve("gl-report.json");

        // When
        service.exportReportToJSON(report, jsonPath);

        // Then
        assertTrue(Files.exists(jsonPath));
        String content = Files.readString(jsonPath);
        assertTrue(content.contains("\"month\""));
        assertTrue(content.contains("\"totalDebits\""));
        assertTrue(content.contains("\"totalCredits\""));
    }

    @Test
    @DisplayName("Should validate balanced report as true")
    void testValidateBalancedReport() throws Exception {
        // Given - Balanced report
        YearMonth month = YearMonth.of(2025, 8);
        List<EntityWithMetadata<Accrual>> accruals = createSampleAccruals();

        when(entityService.findAll(any(ModelSpec.class), eq(Accrual.class)))
                .thenReturn(accruals);

        GLMonthlyReport report = service.aggregateMonthlyJournals(month);

        // When
        boolean isValid = service.validateReportBalance(report);

        // Then
        assertTrue(isValid);
    }

    // Helper methods

    private List<EntityWithMetadata<Accrual>> createSampleAccruals() {
        List<EntityWithMetadata<Accrual>> accruals = new ArrayList<>();

        // Create 3 sample accruals for August 2025
        accruals.add(new EntityWithMetadata<>(
                createAccrual("ACC-001", "LOAN-001", LocalDate.of(2025, 8, 15),
                        "USD", new BigDecimal("100.00"), false),
                createMetadata()
        ));

        accruals.add(new EntityWithMetadata<>(
                createAccrual("ACC-002", "LOAN-002", LocalDate.of(2025, 8, 16),
                        "USD", new BigDecimal("200.00"), false),
                createMetadata()
        ));

        accruals.add(new EntityWithMetadata<>(
                createAccrual("ACC-003", "LOAN-003", LocalDate.of(2025, 8, 17),
                        "USD", new BigDecimal("150.00"), false),
                createMetadata()
        ));

        return accruals;
    }

    private Accrual createAccrual(String accrualId, String loanId, LocalDate asOfDate,
                                  String currency, BigDecimal interestAmount, boolean priorPeriodFlag) {
        Accrual accrual = new Accrual();
        accrual.setAccrualId(accrualId);
        accrual.setLoanId(loanId);
        accrual.setAsOfDate(asOfDate);
        accrual.setCurrency(currency);
        accrual.setInterestAmount(interestAmount);
        accrual.setPriorPeriodFlag(priorPeriodFlag);

        // Create journal entries (DR and CR)
        List<JournalEntry> entries = new ArrayList<>();

        // DR INTEREST_RECEIVABLE
        JournalEntry drEntry = new JournalEntry();
        drEntry.setEntryId("ENTRY-" + accrualId + "-DR");
        drEntry.setAccount(JournalEntryAccount.INTEREST_RECEIVABLE);
        drEntry.setDirection(JournalEntryDirection.DR);
        drEntry.setAmount(interestAmount);
        drEntry.setKind(JournalEntryKind.ORIGINAL);
        entries.add(drEntry);

        // CR INTEREST_INCOME
        JournalEntry crEntry = new JournalEntry();
        crEntry.setEntryId("ENTRY-" + accrualId + "-CR");
        crEntry.setAccount(JournalEntryAccount.INTEREST_INCOME);
        crEntry.setDirection(JournalEntryDirection.CR);
        crEntry.setAmount(interestAmount);
        crEntry.setKind(JournalEntryKind.ORIGINAL);
        entries.add(crEntry);

        accrual.setJournalEntries(entries);

        return accrual;
    }

    private EntityMetadata createMetadata() {
        EntityMetadata metadata = new EntityMetadata();
        metadata.setId(java.util.UUID.randomUUID());
        metadata.setState("POSTED");
        metadata.setCreationDate(new java.util.Date());
        return metadata;
    }
}

