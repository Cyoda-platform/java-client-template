package com.java_template.application.criterion;

import com.java_template.application.entity.pet.version_1.Pet;
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
 * ValidAdoptionCriterion - Validates adoption requirements are met
 * 
 * This criterion checks that all requirements for pet adoption are satisfied
 * before allowing the transition from reserved to adopted state.
 */
@Component
public class ValidAdoptionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidAdoptionCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ValidAdoption criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Pet.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for pet adoption
     * 
     * Validates that:
     * - Pet is in reserved state
     * - Pet has valid adoption fee
     * - Pet is healthy and ready for adoption
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        Pet pet = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if pet entity is null
        if (pet == null) {
            logger.warn("Pet entity is null");
            return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if pet is valid
        if (!pet.isValid()) {
            logger.warn("Pet {} is not valid", pet.getName());
            return EvaluationOutcome.fail("Pet is not valid", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if pet is in reserved state
        if (!"reserved".equals(currentState)) {
            logger.warn("Pet {} is not in reserved state (current: {})", pet.getName(), currentState);
            return EvaluationOutcome.fail("Pet must be in reserved state for adoption", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if adoption fee is valid
        if (pet.getAdoptionFee() == null || pet.getAdoptionFee() < 0) {
            logger.warn("Pet {} has invalid adoption fee: {}", pet.getName(), pet.getAdoptionFee());
            return EvaluationOutcome.fail("Pet must have valid adoption fee", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if medical history is available (required for transparency)
        if (pet.getMedicalHistory() == null || pet.getMedicalHistory().trim().isEmpty()) {
            logger.warn("Pet {} has no medical history", pet.getName());
            return EvaluationOutcome.fail("Pet must have medical history for adoption", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Pet {} passed all adoption validation criteria", pet.getName());
        return EvaluationOutcome.success();
    }
}
