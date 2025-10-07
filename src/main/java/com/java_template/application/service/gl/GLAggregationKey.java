package com.java_template.application.service.gl;

import com.java_template.application.entity.accrual.version_1.JournalEntryAccount;
import com.java_template.application.entity.accrual.version_1.JournalEntryDirection;
import lombok.Data;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Composite key for grouping journal entries in GL aggregation.
 * 
 * <p>As specified in section 8 of the requirements, journal entries are grouped by:
 * (asOfDate, account, direction, currency, priorPeriodFlag)</p>
 * 
 * <p>Note: asOfDate, currency, and priorPeriodFlag are inherited from the parent Accrual,
 * while account and direction come from the journal entry itself.</p>
 * 
 * @see com.java_template.application.entity.accrual.version_1.Accrual
 * @see com.java_template.application.entity.accrual.version_1.JournalEntry
 */
@Data
public class GLAggregationKey {
    
    /**
     * Business date for which the accrual was calculated (inherited from Accrual)
     */
    private final LocalDate asOfDate;
    
    /**
     * General ledger account (from JournalEntry)
     */
    private final JournalEntryAccount account;
    
    /**
     * Direction of the entry - DR or CR (from JournalEntry)
     */
    private final JournalEntryDirection direction;
    
    /**
     * Currency code (inherited from Accrual)
     */
    private final String currency;
    
    /**
     * Flag indicating if this is a prior-period adjustment (inherited from Accrual)
     */
    private final boolean priorPeriodFlag;

    /**
     * Constructor for creating an aggregation key.
     * 
     * @param asOfDate Business date from parent Accrual
     * @param account GL account from journal entry
     * @param direction DR or CR from journal entry
     * @param currency Currency code from parent Accrual
     * @param priorPeriodFlag PPA flag from parent Accrual
     */
    public GLAggregationKey(LocalDate asOfDate, JournalEntryAccount account, 
                           JournalEntryDirection direction, String currency, 
                           boolean priorPeriodFlag) {
        this.asOfDate = asOfDate;
        this.account = account;
        this.direction = direction;
        this.currency = currency;
        this.priorPeriodFlag = priorPeriodFlag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GLAggregationKey that = (GLAggregationKey) o;
        return priorPeriodFlag == that.priorPeriodFlag &&
                Objects.equals(asOfDate, that.asOfDate) &&
                account == that.account &&
                direction == that.direction &&
                Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(asOfDate, account, direction, currency, priorPeriodFlag);
    }

    @Override
    public String toString() {
        return String.format("GLAggregationKey{asOfDate=%s, account=%s, direction=%s, currency=%s, priorPeriodFlag=%s}",
                asOfDate, account, direction, currency, priorPeriodFlag);
    }
}

