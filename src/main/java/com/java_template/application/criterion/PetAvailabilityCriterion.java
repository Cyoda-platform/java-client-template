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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ABOUTME: Criterion that checks if a pet is available for reservation,
 * validating the pet is in available state and meets reservation requirements.
 */
@Component
public class PetAvailabilityCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(PetAvailabilityCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public PetAvailabilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking pet availability for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(Pet.class, this::validatePetAvailability)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates if the pet is available for reservation
     */
    private EvaluationOutcome validatePetAvailability(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        Pet pet = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        logger.debug("Evaluating availability for pet: {} in state: {}", pet.getPetId(), currentState);

        // Check if entity is null (structural validation)
        if (pet == null) {
            logger.warn("Pet entity is null");
            return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!pet.isValid()) {
            logger.warn("Pet entity is not valid");
            return EvaluationOutcome.fail("Pet entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Pet must be in available state
        if (!"available".equals(currentState)) {
            logger.warn("Pet {} is not in available state: {}", pet.getPetId(), currentState);
            return EvaluationOutcome.fail("Pet is not available for reservation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if pet has required photos
        if (pet.getPhotoUrls() == null || pet.getPhotoUrls().isEmpty()) {
            logger.warn("Pet {} does not have required photos", pet.getPetId());
            return EvaluationOutcome.fail("Pet must have at least one photo", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.debug("Pet {} availability check passed", pet.getPetId());
        return EvaluationOutcome.success();
    }
}
