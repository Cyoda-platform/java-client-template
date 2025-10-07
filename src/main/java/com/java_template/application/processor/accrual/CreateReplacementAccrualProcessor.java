package com.java_template.application.processor.accrual;

import com.java_template.application.entity.accrual.version_1.*;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
 * Processor to create a new Accrual for the same asOfDate with REPLACEMENT journal entries.
 *
 * This processor is used during the supersedence workflow when a POSTED accrual
 * needs to be corrected. It:
 * 1. Creates a new Accrual entity for the same loanId and asOfDate
 * 2. Sets supersedesAccrualId to the current accrual's accrualId
 * 3. Copies relevant fields from the current accrual (principal, APR, etc.)
 * 4. Creates REPLACEMENT journal entries with corrected amounts
 * 5. Uses EntityService to create the new accrual entity
 *
 * The new accrual will go through the normal workflow (CALCULATE -> WRITE_JOURNALS -> POSTED)
 * to recalculate interest with current loan data and post the corrected amounts.
 *
 * CRITICAL: This processor creates a NEW entity via EntityService, not updating the current one.
 *
 * This processor runs in ASYNC_NEW_TX mode on accruals calculation nodes.
 */
@Component
public class CreateReplacementAccrualProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateReplacementAccrualProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateReplacementAccrualProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CreateReplacementAccrual for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Accrual.class)
            .validate(this::isValidEntityWithMetadata, "Invalid accrual entity")
            .map(this::createReplacementAccrual)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CreateReplacementAccrual".equalsIgnoreCase(modelSpec.operationName());
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
     * Creates a new replacement accrual that supersedes the current one.
     */
    private EntityWithMetadata<Accrual> createReplacementAccrual(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Accrual> context) {

        EntityWithMetadata<Accrual> entityWithMetadata = context.entityResponse();
        Accrual currentAccrual = entityWithMetadata.entity();

        logger.debug("Creating replacement accrual for accrual: {}", currentAccrual.getAccrualId());

        // Create new accrual entity
        Accrual replacementAccrual = new Accrual();

        // Generate new accrual ID
        replacementAccrual.setAccrualId(UUID.randomUUID().toString());

        // Set supersedence link
        replacementAccrual.setSupersedesAccrualId(currentAccrual.getAccrualId());

        // Copy core fields from current accrual
        replacementAccrual.setLoanId(currentAccrual.getLoanId());
        replacementAccrual.setAsOfDate(currentAccrual.getAsOfDate());
        replacementAccrual.setCurrency(currentAccrual.getCurrency());
        replacementAccrual.setDayCountConvention(currentAccrual.getDayCountConvention());
        replacementAccrual.setRunId(currentAccrual.getRunId());
        replacementAccrual.setPriorPeriodFlag(currentAccrual.getPriorPeriodFlag());


        // Copy principal snapshot (will be recalculated if needed)
        replacementAccrual.setPrincipalSnapshot(currentAccrual.getPrincipalSnapshot());

        // APR and other calculation fields will be recalculated by the workflow
        // Don't copy interestAmount or dayCountFraction - let the workflow recalculate

        // Set timestamps
        replacementAccrual.setPostingTimestamp(null); // Will be set when posted

        // Initialize empty journal entries list
        // The workflow will populate this with REPLACEMENT entries
        replacementAccrual.setJournalEntries(new ArrayList<>());

        // No error information for new accrual
        replacementAccrual.setError(null);

        logger.info("Created replacement accrual {} superseding {} for loan {} on {}",
            replacementAccrual.getAccrualId(), currentAccrual.getAccrualId(),
            replacementAccrual.getLoanId(), replacementAccrual.getAsOfDate());

        // Create the new accrual entity via EntityService
        try {
            EntityWithMetadata<Accrual> createdAccrual = entityService.create(replacementAccrual);

            logger.info("Replacement accrual {} created successfully with technical ID: {}",
                replacementAccrual.getAccrualId(),
                createdAccrual.metadata().getId());

        } catch (Exception e) {
            logger.error("Error creating replacement accrual for current accrual {}: {}",
                currentAccrual.getAccrualId(), e.getMessage());
            throw new IllegalStateException("Failed to create replacement accrual", e);
        }

        // Return the current accrual (unchanged)
        // The current accrual will transition to SUPERSEDED state after this processor completes
        return entityWithMetadata;
    }

    /**
     * Creates REPLACEMENT journal entries with corrected amounts.
     *
     * Note: This method is currently not used because the replacement accrual
     * goes through the normal workflow which will create its own ORIGINAL entries.
     *
     * In a more sophisticated implementation, you might want to create REPLACEMENT
     * entries here with the corrected amounts, but that would require recalculating
     * interest in this processor, which duplicates the workflow logic.
     *
     * The current approach is cleaner: let the new accrual go through the full
     * workflow to ensure all calculations are consistent.
     */
    @SuppressWarnings("unused")
    private List<JournalEntry> createReplacementEntries(BigDecimal correctedAmount) {
        List<JournalEntry> entries = new ArrayList<>();

        // Create DR entry for INTEREST_RECEIVABLE
        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setEntryId(UUID.randomUUID().toString());
        debitEntry.setAccount(JournalEntryAccount.INTEREST_RECEIVABLE);
        debitEntry.setDirection(JournalEntryDirection.DR);
        debitEntry.setAmount(correctedAmount);
        debitEntry.setKind(JournalEntryKind.REPLACEMENT);
        debitEntry.setAdjustsEntryId(null); // Could link to original if needed
        debitEntry.setMemo("Replacement entry - corrected amount");

        // Create CR entry for INTEREST_INCOME
        JournalEntry creditEntry = new JournalEntry();
        creditEntry.setEntryId(UUID.randomUUID().toString());
        creditEntry.setAccount(JournalEntryAccount.INTEREST_INCOME);
        creditEntry.setDirection(JournalEntryDirection.CR);
        creditEntry.setAmount(correctedAmount);
        creditEntry.setKind(JournalEntryKind.REPLACEMENT);
        creditEntry.setAdjustsEntryId(null); // Could link to original if needed
        creditEntry.setMemo("Replacement entry - corrected amount");

        entries.add(debitEntry);
        entries.add(creditEntry);

        return entries;
    }
}

