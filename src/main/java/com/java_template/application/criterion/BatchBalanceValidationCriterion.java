package com.java_template.application.criterion;

import com.java_template.application.entity.gl_batch.version_1.GLBatch;
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

import java.math.BigDecimal;

/**
 * ABOUTME: This criterion validates that a GLBatch is balanced (total debits equal total credits)
 * before allowing maker approval. This is a critical control for GL batch processing.
 */
@Component
public class BatchBalanceValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public BatchBalanceValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking GLBatch balance validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(GLBatch.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context) {
        GLBatch batch = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (batch == null) {
            logger.warn("GLBatch entity is null");
            return EvaluationOutcome.fail("GLBatch entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!batch.isValid()) {
            logger.warn("GLBatch entity is not valid: {}", batch.getBatchId());
            return EvaluationOutcome.fail("GLBatch entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if control totals exist
        if (batch.getControlTotals() == null) {
            logger.warn("Control totals are missing for batch: {}", batch.getBatchId());
            return EvaluationOutcome.fail("Control totals are required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        GLBatch.GLControlTotals controlTotals = batch.getControlTotals();

        // Validate total debits and credits are present
        if (controlTotals.getTotalDebits() == null) {
            logger.warn("Total debits is missing for batch: {}", batch.getBatchId());
            return EvaluationOutcome.fail("Total debits is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (controlTotals.getTotalCredits() == null) {
            logger.warn("Total credits is missing for batch: {}", batch.getBatchId());
            return EvaluationOutcome.fail("Total credits is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Validate batch is balanced (debits == credits)
        BigDecimal totalDebits = controlTotals.getTotalDebits();
        BigDecimal totalCredits = controlTotals.getTotalCredits();

        if (totalDebits.compareTo(totalCredits) != 0) {
            logger.warn("Batch {} is not balanced. Debits: {}, Credits: {}", 
                batch.getBatchId(), totalDebits, totalCredits);
            return EvaluationOutcome.fail(
                String.format("Batch is not balanced. Debits: %s, Credits: %s", totalDebits, totalCredits),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Validate line item count is positive
        if (controlTotals.getLineItemCount() == null || controlTotals.getLineItemCount() <= 0) {
            logger.warn("Invalid line item count for batch {}: {}", 
                batch.getBatchId(), controlTotals.getLineItemCount());
            return EvaluationOutcome.fail("Line item count must be positive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check isBalanced flag matches actual balance
        if (controlTotals.getIsBalanced() != null && !controlTotals.getIsBalanced()) {
            logger.warn("Batch {} isBalanced flag is false", batch.getBatchId());
            return EvaluationOutcome.fail("Batch isBalanced flag indicates imbalance", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.debug("Batch {} is balanced. Debits: {}, Credits: {}, Lines: {}", 
            batch.getBatchId(), totalDebits, totalCredits, controlTotals.getLineItemCount());

        return EvaluationOutcome.success();
    }
}

