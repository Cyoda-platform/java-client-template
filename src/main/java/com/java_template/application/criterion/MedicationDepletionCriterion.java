package com.java_template.application.criterion;

import com.java_template.application.entity.medication.version_1.Medication;
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
 * Criterion to determine if a medication lot is depleted
 * Evaluates inventory levels to trigger automatic depletion state
 */
@Component
public class MedicationDepletionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MedicationDepletionCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking medication depletion criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Medication.class, this::evaluateDepletionStatus)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Evaluates whether the medication lot is depleted
     * Returns success if depleted, fail if still available
     */
    private EvaluationOutcome evaluateDepletionStatus(
            CriterionSerializer.CriterionEntityEvaluationContext<Medication> context) {
        
        Medication medication = context.entityWithMetadata().entity();

        // Check if entity is null
        if (medication == null) {
            logger.warn("Medication is null");
            return EvaluationOutcome.fail("Medication is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!medication.isValid()) {
            logger.warn("Medication is not valid");
            return EvaluationOutcome.fail("Medication is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check quantity on hand
        Integer quantityOnHand = medication.getQuantityOnHand();
        
        if (quantityOnHand == null) {
            logger.warn("Quantity on hand is null for lot {}", medication.getLotNumber());
            return EvaluationOutcome.fail("Quantity on hand is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check if depleted (quantity is 0 or negative)
        if (quantityOnHand <= 0) {
            logger.info("Medication lot {} is depleted (quantity: {})",
                       medication.getLotNumber(), quantityOnHand);
            return EvaluationOutcome.success();
        }

        // Not depleted
        logger.debug("Medication lot {} is not depleted (quantity: {})", 
                    medication.getLotNumber(), quantityOnHand);
        return EvaluationOutcome.fail("Medication lot is not depleted", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
