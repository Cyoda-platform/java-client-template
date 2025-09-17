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
 * PetReturnEligibilityCriterion - Checks if a sold pet is eligible for return
 * 
 * Transition: return_pet
 * Purpose: Validates pet return eligibility within timeframe
 */
@Component
public class PetReturnEligibilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetReturnEligibilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Pet return eligibility criteria for request: {}", request.getId());
        
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
     * Main validation logic for pet return eligibility
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        Pet pet = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if entity is null (structural validation)
        if (pet == null) {
            logger.warn("Pet entity is null");
            return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify pet is in sold state
        if (!"sold".equals(currentState)) {
            logger.warn("Pet {} is not in sold state, current state: {}", pet.getPetId(), currentState);
            return EvaluationOutcome.fail("Pet is not in sold state for return", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check pet validity
        if (!pet.isValid()) {
            logger.warn("Pet {} is not valid", pet.getPetId());
            return EvaluationOutcome.fail("Pet data is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if return is within allowed timeframe (30 days)
        if (pet.getUpdatedAt() != null) {
            long daysSinceSale = java.time.Duration.between(pet.getUpdatedAt(), java.time.LocalDateTime.now()).toDays();
            if (daysSinceSale > 30) {
                logger.warn("Pet {} return period has expired ({} days since sale)", pet.getPetId(), daysSinceSale);
                return EvaluationOutcome.fail("Pet return period has expired (30 days limit)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        // Check pet health/condition allows return (basic validation)
        if (pet.getVaccinated() != null && !pet.getVaccinated()) {
            logger.warn("Pet {} may not be eligible for return due to vaccination status", pet.getPetId());
            // This is a warning, not a failure - business decision
        }

        logger.debug("Pet {} is eligible for return", pet.getPetId());
        return EvaluationOutcome.success();
    }
}
