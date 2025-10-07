package com.java_template.application.entity.accrual.version_1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for JournalEntry validation logic.
 */
class JournalEntryTest {

    private JournalEntry validEntry;

    @BeforeEach
    void setUp() {
        validEntry = new JournalEntry();
        validEntry.setEntryId("entry-123");
        validEntry.setAccount(JournalEntryAccount.INTEREST_RECEIVABLE);
        validEntry.setDirection(JournalEntryDirection.DR);
        validEntry.setAmount(new BigDecimal("100.00"));
        validEntry.setKind(JournalEntryKind.ORIGINAL);
    }

    @Test
    void testIsValid_withValidOriginalEntry_returnsTrue() {
        assertTrue(validEntry.isValid());
    }

    @Test
    void testIsValid_withNullEntryId_returnsFalse() {
        validEntry.setEntryId(null);
        assertFalse(validEntry.isValid());
    }

    @Test
    void testIsValid_withNullAccount_returnsFalse() {
        validEntry.setAccount(null);
        assertFalse(validEntry.isValid());
    }

    @Test
    void testIsValid_withNullDirection_returnsFalse() {
        validEntry.setDirection(null);
        assertFalse(validEntry.isValid());
    }

    @Test
    void testIsValid_withNullAmount_returnsFalse() {
        validEntry.setAmount(null);
        assertFalse(validEntry.isValid());
    }

    @Test
    void testIsValid_withNullKind_returnsFalse() {
        validEntry.setKind(null);
        assertFalse(validEntry.isValid());
    }

    @Test
    void testIsValid_withZeroAmount_returnsFalse() {
        validEntry.setAmount(BigDecimal.ZERO);
        assertFalse(validEntry.isValid());
    }

    @Test
    void testIsValid_withNegativeAmount_returnsFalse() {
        validEntry.setAmount(new BigDecimal("-100.00"));
        assertFalse(validEntry.isValid());
    }

    @Test
    void testIsValid_withReversalAndAdjustsEntryId_returnsTrue() {
        validEntry.setKind(JournalEntryKind.REVERSAL);
        validEntry.setAdjustsEntryId("original-entry-456");

        assertTrue(validEntry.isValid());
    }

    @Test
    void testIsValid_withReversalAndNoAdjustsEntryId_returnsFalse() {
        validEntry.setKind(JournalEntryKind.REVERSAL);
        validEntry.setAdjustsEntryId(null);

        assertFalse(validEntry.isValid());
    }

    @Test
    void testIsValid_withReplacementEntry_returnsTrue() {
        validEntry.setKind(JournalEntryKind.REPLACEMENT);
        // REPLACEMENT entries don't require adjustsEntryId

        assertTrue(validEntry.isValid());
    }

    @Test
    void testIsValid_withReplacementAndAdjustsEntryId_returnsTrue() {
        // REPLACEMENT entries can optionally have adjustsEntryId
        validEntry.setKind(JournalEntryKind.REPLACEMENT);
        validEntry.setAdjustsEntryId("original-entry-789");

        assertTrue(validEntry.isValid());
    }

    @Test
    void testIsValid_withCreditDirection_returnsTrue() {
        validEntry.setDirection(JournalEntryDirection.CR);
        validEntry.setAccount(JournalEntryAccount.INTEREST_INCOME);

        assertTrue(validEntry.isValid());
    }

    @Test
    void testIsValid_withMemo_returnsTrue() {
        validEntry.setMemo("Daily interest accrual for loan-456");

        assertTrue(validEntry.isValid());
    }

    @Test
    void testIsValid_withoutMemo_returnsTrue() {
        // Memo is optional
        validEntry.setMemo(null);

        assertTrue(validEntry.isValid());
    }

    @Test
    void testIsValid_withAllFieldsPopulated_returnsTrue() {
        validEntry.setEntryId("entry-999");
        validEntry.setAccount(JournalEntryAccount.INTEREST_INCOME);
        validEntry.setDirection(JournalEntryDirection.CR);
        validEntry.setAmount(new BigDecimal("250.75"));
        validEntry.setKind(JournalEntryKind.REVERSAL);
        validEntry.setAdjustsEntryId("prior-entry-888");
        validEntry.setMemo("Reversal of prior accrual");

        assertTrue(validEntry.isValid());
    }
}

