package com.java_template.application.entity.accrual.version_1;

import com.java_template.common.workflow.OperationSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EODAccrualBatch entity validation and business logic.
 */
class EODAccrualBatchTest {

    private EODAccrualBatch validBatch;
    private BatchMetrics metrics;
    private LoanFilter loanFilter;

    @BeforeEach
    void setUp() {
        // Create valid metrics
        metrics = new BatchMetrics();
        metrics.setEligibleLoans(0);
        metrics.setProcessedLoans(0);
        metrics.setAccrualsCreated(0);
        metrics.setPostings(0);
        metrics.setDebited(BigDecimal.ZERO);
        metrics.setCredited(BigDecimal.ZERO);
        metrics.setImbalances(0);

        // Create loan filter
        loanFilter = new LoanFilter();
        loanFilter.setLoanIds(new ArrayList<>());
        loanFilter.setProductCodes(new ArrayList<>());

        // Create a valid batch
        validBatch = new EODAccrualBatch();
        validBatch.setBatchId(UUID.randomUUID());
        validBatch.setAsOfDate(LocalDate.of(2025, 10, 7));
        validBatch.setMode(BatchMode.TODAY);
        validBatch.setInitiatedBy("user123");
        validBatch.setLoanFilter(loanFilter);
        validBatch.setPeriodStatus(PeriodStatus.OPEN);
        validBatch.setMetrics(metrics);
    }

    @Test
    void testGetModelKey_returnsCorrectValues() {
        OperationSpecification modelKey = validBatch.getModelKey();

        assertNotNull(modelKey);
        assertTrue(modelKey instanceof OperationSpecification.Entity);

        OperationSpecification.Entity entitySpec = (OperationSpecification.Entity) modelKey;
        assertEquals("EODAccrualBatch", entitySpec.getModelKey().getName());
        assertEquals(1, entitySpec.getModelKey().getVersion());
    }

    @Test
    void testIsValid_withValidBatch_returnsTrue() {
        assertTrue(validBatch.isValid(null));
    }

    @Test
    void testIsValid_withNullAsOfDate_returnsFalse() {
        validBatch.setAsOfDate(null);
        assertFalse(validBatch.isValid(null));
    }

    @Test
    void testIsValid_withNullMode_returnsFalse() {
        validBatch.setMode(null);
        assertFalse(validBatch.isValid(null));
    }

    @Test
    void testIsValid_withNullInitiatedBy_returnsFalse() {
        validBatch.setInitiatedBy(null);
        assertFalse(validBatch.isValid(null));
    }

    @Test
    void testIsValid_withEmptyInitiatedBy_returnsFalse() {
        validBatch.setInitiatedBy("   ");
        assertFalse(validBatch.isValid(null));
    }

    @Test
    void testIsValid_withNullMetrics_returnsFalse() {
        validBatch.setMetrics(null);
        assertFalse(validBatch.isValid(null));
    }

    @Test
    void testIsValid_withBackdatedModeAndReasonCode_returnsTrue() {
        validBatch.setMode(BatchMode.BACKDATED);
        validBatch.setReasonCode("DATA_CORRECTION");
        assertTrue(validBatch.isValid(null));
    }

    @Test
    void testIsValid_withBackdatedModeAndNullReasonCode_returnsFalse() {
        validBatch.setMode(BatchMode.BACKDATED);
        validBatch.setReasonCode(null);
        assertFalse(validBatch.isValid(null));
    }

    @Test
    void testIsValid_withBackdatedModeAndEmptyReasonCode_returnsFalse() {
        validBatch.setMode(BatchMode.BACKDATED);
        validBatch.setReasonCode("   ");
        assertFalse(validBatch.isValid(null));
    }

    @Test
    void testIsValid_withTodayModeAndNullReasonCode_returnsTrue() {
        validBatch.setMode(BatchMode.TODAY);
        validBatch.setReasonCode(null);
        assertTrue(validBatch.isValid(null));
    }

    @Test
    void testIsValid_withTodayModeAndReasonCode_returnsTrue() {
        // ReasonCode is optional for TODAY mode
        validBatch.setMode(BatchMode.TODAY);
        validBatch.setReasonCode("OPTIONAL_REASON");
        assertTrue(validBatch.isValid(null));
    }

    @Test
    void testIsValid_withAllRequiredFieldsSet_returnsTrue() {
        EODAccrualBatch batch = new EODAccrualBatch();
        batch.setAsOfDate(LocalDate.of(2025, 10, 7));
        batch.setMode(BatchMode.TODAY);
        batch.setInitiatedBy("user456");
        batch.setMetrics(new BatchMetrics());

        assertTrue(batch.isValid(null));
    }

    @Test
    void testIsValid_withBackdatedModeAndAllFields_returnsTrue() {
        validBatch.setMode(BatchMode.BACKDATED);
        validBatch.setReasonCode("PRINCIPAL_CORRECTION");
        validBatch.setCascadeFromDate(LocalDate.of(2025, 10, 8));
        validBatch.setPeriodStatus(PeriodStatus.CLOSED);

        assertTrue(validBatch.isValid(null));
    }

    @Test
    void testIsValid_withOptionalFieldsNull_returnsTrue() {
        // Optional fields can be null
        validBatch.setLoanFilter(null);
        validBatch.setPeriodStatus(null);
        validBatch.setCascadeFromDate(null);
        validBatch.setReportId(null);
        validBatch.setReasonCode(null);

        assertTrue(validBatch.isValid(null));
    }

    @Test
    void testBatchMetrics_initialization() {
        BatchMetrics testMetrics = new BatchMetrics();
        testMetrics.setEligibleLoans(100);
        testMetrics.setProcessedLoans(95);
        testMetrics.setAccrualsCreated(95);
        testMetrics.setPostings(190);
        testMetrics.setDebited(new BigDecimal("5000.00"));
        testMetrics.setCredited(new BigDecimal("5000.00"));
        testMetrics.setImbalances(0);

        validBatch.setMetrics(testMetrics);

        assertTrue(validBatch.isValid(null));
        assertEquals(100, validBatch.getMetrics().getEligibleLoans());
        assertEquals(95, validBatch.getMetrics().getProcessedLoans());
    }

    @Test
    void testLoanFilter_withSpecificLoans() {
        LoanFilter filter = new LoanFilter();
        filter.setLoanIds(new ArrayList<>());
        filter.getLoanIds().add(UUID.randomUUID());
        filter.getLoanIds().add(UUID.randomUUID());

        validBatch.setLoanFilter(filter);

        assertTrue(validBatch.isValid(null));
        assertEquals(2, validBatch.getLoanFilter().getLoanIds().size());
    }

    @Test
    void testLoanFilter_withProductCodes() {
        LoanFilter filter = new LoanFilter();
        filter.setProductCodes(new ArrayList<>());
        filter.getProductCodes().add("COMMERCIAL_LOAN");
        filter.getProductCodes().add("TERM_LOAN");

        validBatch.setLoanFilter(filter);

        assertTrue(validBatch.isValid(null));
        assertEquals(2, validBatch.getLoanFilter().getProductCodes().size());
    }


    @Test
    void testPeriodStatus_values() {
        // Test both period status values
        validBatch.setPeriodStatus(PeriodStatus.OPEN);
        assertTrue(validBatch.isValid(null));

        validBatch.setPeriodStatus(PeriodStatus.CLOSED);
        assertTrue(validBatch.isValid(null));
    }

    @Test
    void testBatchMode_values() {
        // Test both batch mode values
        validBatch.setMode(BatchMode.TODAY);
        assertTrue(validBatch.isValid(null));

        validBatch.setMode(BatchMode.BACKDATED);
        validBatch.setReasonCode("REQUIRED_FOR_BACKDATED");
        assertTrue(validBatch.isValid(null));
    }
}

