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
 * ABOUTME: Criterion that checks if a pet sale can be completed,
 * validating the pet is in pending state and meets sale completion requirements.
 */
@Component
public class PetSaleEligibilityCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(PetSaleEligibilityCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public PetSaleEligibilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking pet sale eligibility for request: {}", request.getId());

        return serializer.withRequest(request)
                .evaluateEntity(Pet.class, this::validatePetSaleEligibility)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates if the pet sale can be completed
     */
    private EvaluationOutcome validatePetSaleEligibility(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        Pet pet = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        logger.debug("Evaluating sale eligibility for pet: {} in state: {}", pet.getPetId(), currentState);

        // Check if entity is null (structural validation)
        if (pet == null) {
            logger.warn("Pet entity is null");
            return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!pet.isValid()) {
            logger.warn("Pet entity is not valid");
            return EvaluationOutcome.fail("Pet entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Pet must be in pending state (reserved)
        if (!"pending".equals(currentState)) {
            logger.warn("Pet {} is not in pending state: {}", pet.getPetId(), currentState);
            return EvaluationOutcome.fail("Pet must be in pending state to complete sale", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if pet has required photos
        if (pet.getPhotoUrls() == null || pet.getPhotoUrls().isEmpty()) {
            logger.warn("Pet {} does not have required photos", pet.getPetId());
            return EvaluationOutcome.fail("Pet must have at least one photo", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.debug("Pet {} sale eligibility check passed", pet.getPetId());
        return EvaluationOutcome.success();
    }
}
