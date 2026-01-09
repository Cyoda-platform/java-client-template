package com.java_template.application.criterion;

import com.example.application.entity.transaction.version_1.Transaction;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * IsWatchlistHitCriterion - Evaluates if a transaction has watchlist matches
 * 
 * Checks if transaction has watchlist matches (OFAC, EU sanctions, etc.).
 * A transaction has a watchlist hit if matchedWatchlistIds list is not empty.
 */
@Component
public class IsWatchlistHitCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsWatchlistHitCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking IsWatchlistHitCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Transaction.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates if transaction has watchlist matches
     * 
     * A transaction has a watchlist hit if:
     * - matchedWatchlistIds list is not null and not empty
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Transaction> context) {
        Transaction transaction = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (transaction == null) {
            logger.warn("Transaction is null");
            return EvaluationOutcome.fail("Transaction entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!transaction.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Transaction is not valid");
            return EvaluationOutcome.fail("Transaction entity is not valid", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check for watchlist matches
        if (transaction.getMatchedWatchlistIds() != null && !transaction.getMatchedWatchlistIds().isEmpty()) {
            logger.info("Transaction {} has watchlist hits: {}", transaction.getId(), transaction.getMatchedWatchlistIds());
            return EvaluationOutcome.success();
        }

        logger.debug("Transaction {} has no watchlist matches", transaction.getId());
        return EvaluationOutcome.fail(
            "Transaction has no watchlist matches",
            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
        );
    }
}

