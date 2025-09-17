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
 * PetAvailabilityCriterion - Checks if a pet is available for reservation or sale
 * 
 * Transitions: reserve_pet, sell_pet_direct
 * Purpose: Validates pet availability and required fields
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

    /**
     * Main validation logic for pet availability
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        Pet pet = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if entity is null (structural validation)
        if (pet == null) {
            logger.warn("Pet entity is null");
            return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify pet is in available state
        if (!"available".equals(currentState)) {
            logger.warn("Pet {} is not in available state, current state: {}", pet.getPetId(), currentState);
            return EvaluationOutcome.fail("Pet is not available for reservation or sale", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check pet validity
        if (!pet.isValid()) {
            logger.warn("Pet {} is not valid", pet.getPetId());
            return EvaluationOutcome.fail("Pet data is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Verify required fields
        if (pet.getPetId() == null || pet.getPetId().trim().isEmpty()) {
            logger.warn("Pet ID is missing");
            return EvaluationOutcome.fail("Pet ID is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (pet.getName() == null || pet.getName().trim().isEmpty()) {
            logger.warn("Pet name is missing for pet {}", pet.getPetId());
            return EvaluationOutcome.fail("Pet name is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (pet.getPhotoUrls() == null) {
            logger.warn("Pet photo URLs are missing for pet {}", pet.getPetId());
            return EvaluationOutcome.fail("Pet photo URLs are required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        logger.debug("Pet {} is available for reservation/sale", pet.getPetId());
        return EvaluationOutcome.success();
    }
}
