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
 * IsHighRiskTransactionCriterion - Evaluates if a transaction is classified as high-risk
 * 
 * Checks if transaction is high-risk based on score and flags.
 * A transaction is high-risk if score >= 75 or has compliance flags.
 */
@Component
public class IsHighRiskTransactionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsHighRiskTransactionCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking IsHighRiskTransactionCriterion for request: {}", request.getId());

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
     * Validates if transaction is classified as high-risk
     * 
     * A transaction is high-risk if:
     * - Score >= 75, OR
     * - Has compliance flags
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

        // Check score
        if (transaction.getScore() != null && transaction.getScore() >= 75) {
            logger.info("Transaction {} is high-risk due to score: {}", transaction.getId(), transaction.getScore());
            return EvaluationOutcome.success();
        }

        // Check for compliance flags
        if (transaction.getFlags() != null && !transaction.getFlags().isEmpty()) {
            logger.info("Transaction {} is high-risk due to compliance flags: {}", transaction.getId(), transaction.getFlags());
            return EvaluationOutcome.success();
        }

        logger.debug("Transaction {} is not classified as high-risk", transaction.getId());
        return EvaluationOutcome.fail(
            "Transaction is not high-risk (score < 75 and no compliance flags)",
            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
        );
    }
}

