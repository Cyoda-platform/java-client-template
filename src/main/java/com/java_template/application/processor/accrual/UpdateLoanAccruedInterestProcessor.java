package com.java_template.application.processor.accrual;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.JournalEntry;
import com.java_template.application.entity.accrual.version_1.JournalEntryDirection;
import com.java_template.application.entity.accrual.version_1.JournalEntryKind;
import com.java_template.application.entity.loan.version_1.Loan;
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

import java.math.BigDecimal;
import java.util.List;

/**
 * Processor to update the loan's accruedInterest balance from the net delta of journal entries.
 *
 * This processor:
 * 1. Reads the current loan entity using EntityService
 * 2. Calculates the net delta from journal entries (considering REVERSAL and REPLACEMENT kinds)
 * 3. Updates the loan's accruedInterest balance
 * 4. Saves the updated loan entity using EntityService
 *
 * CRITICAL: This processor updates OTHER entities (Loan), NOT the current accrual entity.
 *
 * Net delta calculation:
 * - ORIGINAL entries: Add to balance (DR increases receivable, CR decreases)
 * - REVERSAL entries: Reverse the effect (opposite of original)
 * - REPLACEMENT entries: Add to balance (like ORIGINAL)
 *
 * This processor runs in ASYNC_NEW_TX mode on ledger nodes.
 */
@Component
public class UpdateLoanAccruedInterestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateLoanAccruedInterestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public UpdateLoanAccruedInterestProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing UpdateLoanAccruedInterest for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Accrual.class)
            .validate(this::isValidEntityWithMetadata, "Invalid accrual entity")
            .map(this::updateLoanAccruedInterest)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "UpdateLoanAccruedInterest".equalsIgnoreCase(modelSpec.operationName());
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
     * Updates the loan's accruedInterest balance based on journal entries.
     */
    private EntityWithMetadata<Accrual> updateLoanAccruedInterest(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Accrual> context) {

        EntityWithMetadata<Accrual> entityWithMetadata = context.entityResponse();
        Accrual accrual = entityWithMetadata.entity();

        logger.debug("Updating loan accrued interest for accrual: {}", accrual.getAccrualId());

        String loanId = accrual.getLoanId();
        List<JournalEntry> journalEntries = accrual.getJournalEntries();

        // Validate required fields
        if (loanId == null || loanId.trim().isEmpty()) {
            logger.error("LoanId is null or empty for accrual: {}", accrual.getAccrualId());
            throw new IllegalStateException("LoanId is required to update loan accrued interest");
        }

        if (journalEntries == null || journalEntries.isEmpty()) {
            logger.warn("No journal entries found for accrual: {}. Skipping loan update.",
                accrual.getAccrualId());
            return entityWithMetadata;
        }

        // Calculate net delta from journal entries
        BigDecimal netDelta = calculateNetDelta(journalEntries);

        logger.debug("Net delta calculated for accrual {}: {}", accrual.getAccrualId(), netDelta);

        // Retrieve the loan entity
        ModelSpec loanModelSpec = new ModelSpec()
            .withName(Loan.ENTITY_NAME)
            .withVersion(Loan.ENTITY_VERSION);

        EntityWithMetadata<Loan> loanWithMetadata;
        try {
            loanWithMetadata = entityService.findByBusinessId(
                loanModelSpec,
                loanId,
                "loanId",
                Loan.class
            );
        } catch (Exception e) {
            logger.error("Error retrieving loan {} for accrual {}: {}",
                loanId, accrual.getAccrualId(), e.getMessage());
            throw new IllegalStateException("Failed to retrieve loan: " + loanId, e);
        }

        if (loanWithMetadata == null) {
            logger.error("Loan {} not found for accrual: {}", loanId, accrual.getAccrualId());
            throw new IllegalStateException("Loan not found: " + loanId);
        }

        Loan loan = loanWithMetadata.entity();
        BigDecimal currentAccruedInterest = loan.getAccruedInterest() != null ?
            loan.getAccruedInterest() : BigDecimal.ZERO;

        // Calculate new accrued interest balance
        BigDecimal newAccruedInterest = currentAccruedInterest.add(netDelta);

        // Update the loan entity
        loan.setAccruedInterest(newAccruedInterest);

        // Save the updated loan entity (NOT the current accrual entity)
        try {
            entityService.update(loanWithMetadata.metadata().getId(), loan, "UPDATE_ACCRUED_INTEREST");
            logger.info("Loan {} accrued interest updated: {} -> {} (delta: {})",
                loanId, currentAccruedInterest, newAccruedInterest, netDelta);
        } catch (Exception e) {
            logger.error("Error updating loan {} for accrual {}: {}",
                loanId, accrual.getAccrualId(), e.getMessage());
            throw new IllegalStateException("Failed to update loan: " + loanId, e);
        }

        return entityWithMetadata;
    }

    /**
     * Calculates the net delta from journal entries.
     *
     * Logic:
     * - ORIGINAL DR entries: Add amount (increases receivable)
     * - ORIGINAL CR entries: Subtract amount (decreases receivable)
     * - REVERSAL entries: Opposite effect of original
     * - REPLACEMENT entries: Same as ORIGINAL
     */
    private BigDecimal calculateNetDelta(List<JournalEntry> journalEntries) {
        BigDecimal netDelta = BigDecimal.ZERO;

        for (JournalEntry entry : journalEntries) {
            BigDecimal amount = entry.getAmount();
            JournalEntryDirection direction = entry.getDirection();
            JournalEntryKind kind = entry.getKind();

            if (amount == null || direction == null || kind == null) {
                logger.warn("Skipping journal entry with null fields: {}", entry.getEntryId());
                continue;
            }

            // Calculate the effect on accrued interest
            BigDecimal effect = switch (kind) {
                case ORIGINAL, REPLACEMENT -> {
                    // DR increases receivable, CR decreases
                    yield direction == JournalEntryDirection.DR ? amount : amount.negate();
                }
                case REVERSAL -> {
                    // Opposite effect: DR decreases, CR increases
                    yield direction == JournalEntryDirection.DR ? amount.negate() : amount;
                }
            };

            netDelta = netDelta.add(effect);

            logger.debug("Entry {}: {} {} {} -> effect: {}, running total: {}",
                entry.getEntryId(), kind, direction, amount, effect, netDelta);
        }

        return netDelta;
    }
}

