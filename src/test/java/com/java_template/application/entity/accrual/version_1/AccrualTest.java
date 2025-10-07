package com.java_template.application.entity.accrual.version_1;

import com.java_template.common.workflow.OperationSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Accrual entity validation and business logic.
 */
class AccrualTest {

    private Accrual validAccrual;
    private PrincipalSnapshot principalSnapshot;
    private List<JournalEntry> balancedEntries;

    @BeforeEach
    void setUp() {
        // Create a valid principal snapshot
        principalSnapshot = new PrincipalSnapshot();
        principalSnapshot.setAmount(new BigDecimal("100000.00"));
        principalSnapshot.setEffectiveAtStartOfDay(true);

        // Create balanced journal entries (DR and CR for same amount)
        balancedEntries = new ArrayList<>();

        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setEntryId("entry-1");
        debitEntry.setAccount(JournalEntryAccount.INTEREST_RECEIVABLE);
        debitEntry.setDirection(JournalEntryDirection.DR);
        debitEntry.setAmount(new BigDecimal("27.40"));
        debitEntry.setKind(JournalEntryKind.ORIGINAL);
        balancedEntries.add(debitEntry);

        JournalEntry creditEntry = new JournalEntry();
        creditEntry.setEntryId("entry-2");
        creditEntry.setAccount(JournalEntryAccount.INTEREST_INCOME);
        creditEntry.setDirection(JournalEntryDirection.CR);
        creditEntry.setAmount(new BigDecimal("27.40"));
        creditEntry.setKind(JournalEntryKind.ORIGINAL);
        balancedEntries.add(creditEntry);

        // Create a valid accrual
        validAccrual = new Accrual();
        validAccrual.setAccrualId("accrual-123");
        validAccrual.setLoanId("loan-456");
        validAccrual.setAsOfDate(LocalDate.of(2025, 10, 7));
        validAccrual.setCurrency("USD");
        validAccrual.setAprId("apr-789");
        validAccrual.setDayCountConvention(DayCountConvention.ACT_360);
        validAccrual.setDayCountFraction(new BigDecimal("0.002777778"));
        validAccrual.setPrincipalSnapshot(principalSnapshot);
        validAccrual.setInterestAmount(new BigDecimal("27.40"));
        validAccrual.setPriorPeriodFlag(false);
        validAccrual.setVersion(1);
        validAccrual.setState(AccrualState.CALCULATED);
        validAccrual.setJournalEntries(new ArrayList<>());
    }

    @Test
    void testGetModelKey_returnsCorrectValues() {
        OperationSpecification modelKey = validAccrual.getModelKey();

        assertNotNull(modelKey);
        assertTrue(modelKey instanceof OperationSpecification.Entity);

        OperationSpecification.Entity entitySpec = (OperationSpecification.Entity) modelKey;
        assertEquals("Accrual", entitySpec.getModelKey().getName());
        assertEquals(1, entitySpec.getModelKey().getVersion());
    }

    @Test
    void testIsValid_withValidAccrual_returnsTrue() {
        assertTrue(validAccrual.isValid());
    }

    @Test
    void testIsValid_withNullLoanId_returnsFalse() {
        validAccrual.setLoanId(null);
        assertFalse(validAccrual.isValid());
    }

    @Test
    void testIsValid_withEmptyLoanId_returnsFalse() {
        validAccrual.setLoanId("   ");
        assertFalse(validAccrual.isValid());
    }

    @Test
    void testIsValid_withNullAsOfDate_returnsFalse() {
        validAccrual.setAsOfDate(null);
        assertFalse(validAccrual.isValid());
    }

    @Test
    void testIsValid_withNullCurrency_returnsFalse() {
        validAccrual.setCurrency(null);
        assertFalse(validAccrual.isValid());
    }

    @Test
    void testIsValid_withEmptyCurrency_returnsFalse() {
        validAccrual.setCurrency("");
        assertFalse(validAccrual.isValid());
    }

    @Test
    void testIsValid_withInvalidCurrencyCode_returnsFalse() {
        validAccrual.setCurrency("INVALID");
        assertFalse(validAccrual.isValid());
    }

    @Test
    void testIsValid_withValidCurrencyCodes_returnsTrue() {
        // Test common valid ISO-4217 codes
        String[] validCurrencies = {"USD", "EUR", "GBP", "JPY", "CHF"};

        for (String currency : validCurrencies) {
            validAccrual.setCurrency(currency);
            assertTrue(validAccrual.isValid(), "Currency " + currency + " should be valid");
        }
    }

    @Test
    void testIsValid_withPostedStateAndBalancedEntries_returnsTrue() {
        validAccrual.setState(AccrualState.POSTED);
        validAccrual.setJournalEntries(balancedEntries);
        validAccrual.setPostingTimestamp(OffsetDateTime.now());

        assertTrue(validAccrual.isValid());
    }

    @Test
    void testIsValid_withPostedStateAndUnbalancedEntries_returnsFalse() {
        validAccrual.setState(AccrualState.POSTED);

        // Create unbalanced entries (DR > CR)
        List<JournalEntry> unbalancedEntries = new ArrayList<>();

        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setEntryId("entry-1");
        debitEntry.setAccount(JournalEntryAccount.INTEREST_RECEIVABLE);
        debitEntry.setDirection(JournalEntryDirection.DR);
        debitEntry.setAmount(new BigDecimal("100.00"));
        debitEntry.setKind(JournalEntryKind.ORIGINAL);
        unbalancedEntries.add(debitEntry);

        JournalEntry creditEntry = new JournalEntry();
        creditEntry.setEntryId("entry-2");
        creditEntry.setAccount(JournalEntryAccount.INTEREST_INCOME);
        creditEntry.setDirection(JournalEntryDirection.CR);
        creditEntry.setAmount(new BigDecimal("50.00"));
        creditEntry.setKind(JournalEntryKind.ORIGINAL);
        unbalancedEntries.add(creditEntry);

        validAccrual.setJournalEntries(unbalancedEntries);

        assertFalse(validAccrual.isValid());
    }

    @Test
    void testIsValid_withPostedStateAndNoEntries_returnsTrue() {
        // Edge case: POSTED with no entries should still be valid
        // (balance of zero equals zero)
        validAccrual.setState(AccrualState.POSTED);
        validAccrual.setJournalEntries(new ArrayList<>());
        validAccrual.setPostingTimestamp(OffsetDateTime.now());

        assertTrue(validAccrual.isValid());
    }

    @Test
    void testIsValid_withInvalidJournalEntry_returnsFalse() {
        // Create an invalid journal entry (REVERSAL without adjustsEntryId)
        JournalEntry invalidEntry = new JournalEntry();
        invalidEntry.setEntryId("entry-1");
        invalidEntry.setAccount(JournalEntryAccount.INTEREST_RECEIVABLE);
        invalidEntry.setDirection(JournalEntryDirection.DR);
        invalidEntry.setAmount(new BigDecimal("27.40"));
        invalidEntry.setKind(JournalEntryKind.REVERSAL);
        // Missing adjustsEntryId - this makes it invalid

        List<JournalEntry> entries = new ArrayList<>();
        entries.add(invalidEntry);

        validAccrual.setJournalEntries(entries);

        assertFalse(validAccrual.isValid());
    }

    @Test
    void testIsValid_withMultipleBalancedEntries_returnsTrue() {
        validAccrual.setState(AccrualState.POSTED);

        // Create multiple balanced entries (2 DR + 2 CR = balanced)
        List<JournalEntry> multipleEntries = new ArrayList<>();

        JournalEntry dr1 = new JournalEntry();
        dr1.setEntryId("entry-1");
        dr1.setAccount(JournalEntryAccount.INTEREST_RECEIVABLE);
        dr1.setDirection(JournalEntryDirection.DR);
        dr1.setAmount(new BigDecimal("50.00"));
        dr1.setKind(JournalEntryKind.ORIGINAL);
        multipleEntries.add(dr1);

        JournalEntry dr2 = new JournalEntry();
        dr2.setEntryId("entry-2");
        dr2.setAccount(JournalEntryAccount.INTEREST_RECEIVABLE);
        dr2.setDirection(JournalEntryDirection.DR);
        dr2.setAmount(new BigDecimal("30.00"));
        dr2.setKind(JournalEntryKind.REPLACEMENT);
        multipleEntries.add(dr2);

        JournalEntry cr1 = new JournalEntry();
        cr1.setEntryId("entry-3");
        cr1.setAccount(JournalEntryAccount.INTEREST_INCOME);
        cr1.setDirection(JournalEntryDirection.CR);
        cr1.setAmount(new BigDecimal("40.00"));
        cr1.setKind(JournalEntryKind.ORIGINAL);
        multipleEntries.add(cr1);

        JournalEntry cr2 = new JournalEntry();
        cr2.setEntryId("entry-4");
        cr2.setAccount(JournalEntryAccount.INTEREST_INCOME);
        cr2.setDirection(JournalEntryDirection.CR);
        cr2.setAmount(new BigDecimal("40.00"));
        cr2.setKind(JournalEntryKind.REPLACEMENT);
        multipleEntries.add(cr2);

        validAccrual.setJournalEntries(multipleEntries);

        assertTrue(validAccrual.isValid());
    }

    @Test
    void testIsValid_withNonPostedStateAndUnbalancedEntries_returnsTrue() {
        // For non-POSTED states, balance check should not apply
        validAccrual.setState(AccrualState.CALCULATED);

        List<JournalEntry> unbalancedEntries = new ArrayList<>();
        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setEntryId("entry-1");
        debitEntry.setAccount(JournalEntryAccount.INTEREST_RECEIVABLE);
        debitEntry.setDirection(JournalEntryDirection.DR);
        debitEntry.setAmount(new BigDecimal("100.00"));
        debitEntry.setKind(JournalEntryKind.ORIGINAL);
        unbalancedEntries.add(debitEntry);

        validAccrual.setJournalEntries(unbalancedEntries);

        assertTrue(validAccrual.isValid());
    }
}

