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
 * PetReservationValidCriterion - Validates that a pet reservation is valid for sale completion
 * 
 * Transition: complete_sale
 * Purpose: Validates pet reservation is valid and not expired
 */
@Component
public class PetReservationValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetReservationValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Pet reservation validity criteria for request: {}", request.getId());
        
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
     * Main validation logic for pet reservation validity
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        Pet pet = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if entity is null (structural validation)
        if (pet == null) {
            logger.warn("Pet entity is null");
            return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify pet is in pending state
        if (!"pending".equals(currentState)) {
            logger.warn("Pet {} is not in pending state, current state: {}", pet.getPetId(), currentState);
            return EvaluationOutcome.fail("Pet is not in pending state for sale completion", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check pet validity
        if (!pet.isValid()) {
            logger.warn("Pet {} is not valid", pet.getPetId());
            return EvaluationOutcome.fail("Pet data is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if reservation is still valid (within 24 hours)
        if (pet.getUpdatedAt() != null) {
            long hoursSinceReservation = java.time.Duration.between(pet.getUpdatedAt(), java.time.LocalDateTime.now()).toHours();
            if (hoursSinceReservation > 24) {
                logger.warn("Pet {} reservation has expired ({} hours old)", pet.getPetId(), hoursSinceReservation);
                return EvaluationOutcome.fail("Pet reservation has expired", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        logger.debug("Pet {} reservation is valid for completion", pet.getPetId());
        return EvaluationOutcome.success();
    }
}
