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
            .evaluateEntity(Pet.class, this::validatePetAvailability)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Check if pet is available for reservation
     * Business rule: pet.healthStatus == "healthy" AND pet.vaccinated == true
     */
    private EvaluationOutcome validatePetAvailability(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
        Pet pet = context.entityWithMetadata().entity();

        // Check if pet is null (structural validation)
        if (pet == null) {
            logger.warn("Pet entity is null");
            return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!pet.isValid()) {
            logger.warn("Pet entity is not valid");
            return EvaluationOutcome.fail("Pet entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check health status
        if (pet.getHealthStatus() == null || !pet.getHealthStatus().equalsIgnoreCase("healthy")) {
            logger.warn("Pet {} is not healthy, current health status: {}", pet.getPetId(), pet.getHealthStatus());
            return EvaluationOutcome.fail(
                String.format("Pet %s is not healthy (current status: %s)", pet.getPetId(), pet.getHealthStatus()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Check vaccination status
        if (pet.getVaccinated() == null || !pet.getVaccinated()) {
            logger.warn("Pet {} is not vaccinated", pet.getPetId());
            return EvaluationOutcome.fail(
                String.format("Pet %s is not vaccinated", pet.getPetId()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        logger.debug("Pet {} is available for reservation", pet.getPetId());
        return EvaluationOutcome.success();
    }
}
