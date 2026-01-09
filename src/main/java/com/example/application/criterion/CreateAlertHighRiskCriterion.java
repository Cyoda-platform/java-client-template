package com.example.application.criterion;

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
 * CreateAlertHighRiskCriterion - Evaluates if an alert should be created for high-risk transactions
 * 
 * Checks if transaction score >= 75 for alert creation in Transaction Monitoring workflow.
 * This criterion determines whether a transaction's risk level warrants alert generation.
 */
@Component
public class CreateAlertHighRiskCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CreateAlertHighRiskCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking CreateAlertHighRiskCriterion for request: {}", request.getId());

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
     * Validates if transaction score meets alert creation threshold (>= 75)
     * 
     * Available StandardEvalReasonCategories:
     * - STRUCTURAL_FAILURE: For null entities, missing required fields
     * - BUSINESS_RULE_FAILURE: For business logic violations
     * - DATA_QUALITY_FAILURE: For data consistency issues
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

        // Check if score is present
        if (transaction.getScore() == null) {
            logger.warn("Transaction score is null");
            return EvaluationOutcome.fail("Transaction score is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        double score = transaction.getScore();
        logger.debug("Transaction {} has risk score: {}", transaction.getId(), score);

        // Check if score >= 75 (alert creation threshold)
        if (score >= 75) {
            logger.info("Transaction {} qualifies for alert creation with score: {}", transaction.getId(), score);
            return EvaluationOutcome.success();
        }

        logger.debug("Transaction {} does not meet alert threshold. Score: {}", transaction.getId(), score);
        return EvaluationOutcome.fail(
            String.format("Risk score %.2f is below alert creation threshold of 75", score),
            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
        );
    }
}

