package com.java_template.application.processor.accrual;

import com.java_template.application.entity.accrual.version_1.*;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Processor to write embedded DR/CR journal entries to the accrual.
 *
 * Creates two journal entries for each accrual:
 * 1. DR entry for INTEREST_RECEIVABLE (debit increases asset)
 * 2. CR entry for INTEREST_INCOME (credit increases revenue)
 *
 * Both entries have the same amount (interestAmount) to maintain balance.
 * Entries are marked as ORIGINAL kind for new accruals.
 *
 * This processor runs in ASYNC_NEW_TX mode on ledger nodes.
 * The entries are embedded in the accrual entity at $.journalEntries.
 */
@Component
public class WriteAccrualJournalEntriesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(WriteAccrualJournalEntriesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public WriteAccrualJournalEntriesProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WriteAccrualJournalEntries for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Accrual.class)
            .validate(this::isValidEntityWithMetadata, "Invalid accrual entity")
            .map(this::writeJournalEntries)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "WriteAccrualJournalEntries".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper.
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Accrual> entityWithMetadata) {
        Accrual entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Writes journal entries to the accrual.
     * Creates DR entry for INTEREST_RECEIVABLE and CR entry for INTEREST_INCOME.
     */
    private EntityWithMetadata<Accrual> writeJournalEntries(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Accrual> context) {

        EntityWithMetadata<Accrual> entityWithMetadata = context.entityResponse();
        Accrual accrual = entityWithMetadata.entity();

        logger.debug("Writing journal entries for accrual: {}", accrual.getAccrualId());

        // Get interest amount
        BigDecimal interestAmount = accrual.getInterestAmount();

        if (interestAmount == null) {
            logger.error("InterestAmount is null for accrual: {}. " +
                "CalculateAccrualAmountProcessor must run before this processor.",
                accrual.getAccrualId());
            throw new IllegalStateException("InterestAmount is required for journal entry creation. " +
                "Ensure CalculateAccrualAmountProcessor has run.");
        }

        // Validate interest amount is positive
        if (interestAmount.compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Interest amount is zero or negative for accrual {}: {}. " +
                "Journal entries will still be created.",
                accrual.getAccrualId(), interestAmount);
        }

        // Initialize journal entries list if null
        List<JournalEntry> journalEntries = accrual.getJournalEntries();
        if (journalEntries == null) {
            journalEntries = new ArrayList<>();
            accrual.setJournalEntries(journalEntries);
        }

        // Create DR entry for INTEREST_RECEIVABLE
        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setEntryId(UUID.randomUUID().toString());
        debitEntry.setAccount(JournalEntryAccount.INTEREST_RECEIVABLE);
        debitEntry.setDirection(JournalEntryDirection.DR);
        debitEntry.setAmount(interestAmount);
        debitEntry.setKind(JournalEntryKind.ORIGINAL);
        debitEntry.setAdjustsEntryId(null); // ORIGINAL entries don't adjust other entries
        debitEntry.setMemo("Daily interest accrual - receivable");

        // Create CR entry for INTEREST_INCOME
        JournalEntry creditEntry = new JournalEntry();
        creditEntry.setEntryId(UUID.randomUUID().toString());
        creditEntry.setAccount(JournalEntryAccount.INTEREST_INCOME);
        creditEntry.setDirection(JournalEntryDirection.CR);
        creditEntry.setAmount(interestAmount);
        creditEntry.setKind(JournalEntryKind.ORIGINAL);
        creditEntry.setAdjustsEntryId(null); // ORIGINAL entries don't adjust other entries
        creditEntry.setMemo("Daily interest accrual - income");

        // Add entries to the list
        journalEntries.add(debitEntry);
        journalEntries.add(creditEntry);

        logger.info("Journal entries created for accrual {}: DR {} {}, CR {} {}",
            accrual.getAccrualId(),
            JournalEntryAccount.INTEREST_RECEIVABLE, interestAmount,
            JournalEntryAccount.INTEREST_INCOME, interestAmount);

        // Verify balance (DR total should equal CR total)
        BigDecimal debitTotal = journalEntries.stream()
            .filter(e -> e.getDirection() == JournalEntryDirection.DR)
            .map(JournalEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal creditTotal = journalEntries.stream()
            .filter(e -> e.getDirection() == JournalEntryDirection.CR)
            .map(JournalEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (debitTotal.compareTo(creditTotal) != 0) {
            logger.error("Journal entries are not balanced for accrual {}: DR={}, CR={}",
                accrual.getAccrualId(), debitTotal, creditTotal);
            throw new IllegalStateException("Journal entries must be balanced (DR total must equal CR total)");
        }

        logger.debug("Journal entries balanced for accrual {}: DR={}, CR={}",
            accrual.getAccrualId(), debitTotal, creditTotal);

        return entityWithMetadata;
    }
}

