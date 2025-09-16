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
 * PetAvailabilityCriterion - Check if pet is available for reservation
 * 
 * Transition: reserve_pet (available → pending)
 * Purpose: Check if pet is available for reservation
 */
@Component
public class PetAvailabilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetAvailabilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Pet availability criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Pet.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        Pet pet = context.entityWithMetadata().entity();

        // Check if pet is null (structural validation)
        if (pet == null) {
            logger.warn("Pet is null");
            return EvaluationOutcome.fail("Pet is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!pet.isValid()) {
            logger.warn("Pet is not valid");
            return EvaluationOutcome.fail("Pet is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if pet has valid petId
        if (pet.getPetId() == null || pet.getPetId().trim().isEmpty()) {
            logger.warn("Pet ID is invalid");
            return EvaluationOutcome.fail("Pet must have valid ID", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if pet has valid price (> 0)
        if (pet.getPrice() == null || pet.getPrice() <= 0) {
            logger.warn("Pet price is invalid: {}", pet.getPrice());
            return EvaluationOutcome.fail("Pet price must be greater than 0", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Note: Pet state verification (available) is handled by the workflow system
        // Category validation would require external service call, which is not recommended in criteria
        
        logger.debug("Pet availability validation passed for pet: {}", pet.getPetId());
        return EvaluationOutcome.success();
    }
}
