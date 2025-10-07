package com.java_template.application.processor.accrual;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.JournalEntry;
import com.java_template.application.entity.accrual.version_1.JournalEntryDirection;
import com.java_template.application.entity.accrual.version_1.JournalEntryKind;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Processor to append equal-and-opposite REVERSAL journal entries.
 * 
 * This processor is used during the supersedence workflow when a POSTED accrual
 * needs to be corrected. It:
 * 1. Queries EntityService for the prior accrual using supersedesAccrualId
 * 2. For each ORIGINAL entry in the prior accrual, creates a REVERSAL entry with opposite direction
 * 3. Sets adjustsEntryId to reference the original entryId
 * 4. Appends the REVERSAL entries to the current accrual's journalEntries
 * 
 * The REVERSAL entries effectively undo the accounting effect of the prior accrual.
 * They are followed by REPLACEMENT entries (created by CreateReplacementAccrualProcessor)
 * that post the corrected amounts.
 * 
 * This processor runs in ASYNC_NEW_TX mode on ledger nodes.
 */
@Component
public class ReversePriorJournalsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReversePriorJournalsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ReversePriorJournalsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReversePriorJournals for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Accrual.class)
            .validate(this::isValidEntityWithMetadata, "Invalid accrual entity")
            .map(this::reversePriorJournals)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ReversePriorJournals".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper.
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Accrual> entityWithMetadata) {
        Accrual entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Creates REVERSAL entries for all ORIGINAL entries in the prior accrual.
     */
    private EntityWithMetadata<Accrual> reversePriorJournals(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Accrual> context) {

        EntityWithMetadata<Accrual> entityWithMetadata = context.entityResponse();
        Accrual currentAccrual = entityWithMetadata.entity();

        logger.debug("Reversing prior journals for accrual: {}", currentAccrual.getAccrualId());

        String supersedesAccrualId = currentAccrual.getSupersedesAccrualId();

        // Validate that this accrual is superseding another
        if (supersedesAccrualId == null || supersedesAccrualId.trim().isEmpty()) {
            logger.error("SupersedesAccrualId is null or empty for accrual: {}. " +
                "This processor should only run during supersedence workflow.", 
                currentAccrual.getAccrualId());
            throw new IllegalStateException("SupersedesAccrualId is required for reversal processing");
        }

        // Retrieve the prior accrual
        ModelSpec accrualModelSpec = new ModelSpec()
            .withName(Accrual.ENTITY_NAME)
            .withVersion(Accrual.ENTITY_VERSION);

        EntityWithMetadata<Accrual> priorAccrualWithMetadata;
        try {
            priorAccrualWithMetadata = entityService.findByBusinessId(
                accrualModelSpec,
                supersedesAccrualId,
                "accrualId",
                Accrual.class
            );
        } catch (Exception e) {
            logger.error("Error retrieving prior accrual {} for current accrual {}: {}", 
                supersedesAccrualId, currentAccrual.getAccrualId(), e.getMessage());
            throw new IllegalStateException("Failed to retrieve prior accrual: " + supersedesAccrualId, e);
        }

        if (priorAccrualWithMetadata == null) {
            logger.error("Prior accrual {} not found for current accrual: {}", 
                supersedesAccrualId, currentAccrual.getAccrualId());
            throw new IllegalStateException("Prior accrual not found: " + supersedesAccrualId);
        }

        Accrual priorAccrual = priorAccrualWithMetadata.entity();
        List<JournalEntry> priorEntries = priorAccrual.getJournalEntries();

        if (priorEntries == null || priorEntries.isEmpty()) {
            logger.warn("No journal entries found in prior accrual {}. Nothing to reverse.", 
                supersedesAccrualId);
            return entityWithMetadata;
        }

        // Initialize journal entries list for current accrual if null
        List<JournalEntry> currentEntries = currentAccrual.getJournalEntries();
        if (currentEntries == null) {
            currentEntries = new ArrayList<>();
            currentAccrual.setJournalEntries(currentEntries);
        }

        // Create REVERSAL entries for each ORIGINAL entry in the prior accrual
        int reversalCount = 0;
        for (JournalEntry priorEntry : priorEntries) {
            // Only reverse ORIGINAL entries (not REVERSAL or REPLACEMENT)
            if (priorEntry.getKind() != JournalEntryKind.ORIGINAL) {
                logger.debug("Skipping non-ORIGINAL entry {} in prior accrual", priorEntry.getEntryId());
                continue;
            }

            // Create reversal entry with opposite direction
            JournalEntry reversalEntry = new JournalEntry();
            reversalEntry.setEntryId(UUID.randomUUID().toString());
            reversalEntry.setAccount(priorEntry.getAccount());
            reversalEntry.setDirection(getOppositeDirection(priorEntry.getDirection()));
            reversalEntry.setAmount(priorEntry.getAmount());
            reversalEntry.setKind(JournalEntryKind.REVERSAL);
            reversalEntry.setAdjustsEntryId(priorEntry.getEntryId()); // Link to original entry
            reversalEntry.setMemo("Reversal of entry " + priorEntry.getEntryId() + 
                " from accrual " + supersedesAccrualId);

            currentEntries.add(reversalEntry);
            reversalCount++;

            logger.debug("Created REVERSAL entry {} for original entry {} (account: {}, direction: {} -> {})",
                reversalEntry.getEntryId(), priorEntry.getEntryId(), 
                priorEntry.getAccount(), priorEntry.getDirection(), reversalEntry.getDirection());
        }

        logger.info("Created {} REVERSAL entries for accrual {} (superseding {})",
            reversalCount, currentAccrual.getAccrualId(), supersedesAccrualId);

        return entityWithMetadata;
    }

    /**
     * Returns the opposite direction for a journal entry.
     */
    private JournalEntryDirection getOppositeDirection(JournalEntryDirection direction) {
        return direction == JournalEntryDirection.DR ? 
            JournalEntryDirection.CR : JournalEntryDirection.DR;
    }
}

