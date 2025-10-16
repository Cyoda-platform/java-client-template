package com.java_template.application.criterion;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriterionCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriterionCalculationResponse;
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
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public EntityCriterionCalculationResponse check(CyodaEventContext<EntityCriterionCalculationRequest> context) {
        EntityCriterionCalculationRequest request = context.getEvent();
        logger.debug("Checking pet sale eligibility for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Pet.class)
                .validate(this::isValidEntityWithMetadata, "Invalid pet entity wrapper")
                .evaluate(this::evaluateSaleEligibility)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(com.java_template.common.dto.EntityWithMetadata<Pet> entityWithMetadata) {
        Pet pet = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return pet != null && pet.isValid() && technicalId != null;
    }

    /**
     * Evaluates if the pet sale can be completed
     */
    private boolean evaluateSaleEligibility(
            CriterionSerializer.CriterionEntityResponseExecutionContext<Pet> context) {

        com.java_template.common.dto.EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet pet = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Evaluating sale eligibility for pet: {} in state: {}", pet.getPetId(), currentState);

        // Pet must be in pending state (reserved)
        boolean isPending = "pending".equals(currentState);

        // Additional business rules can be added here
        // For example: check if payment is processed, documentation is complete, etc.
        boolean hasValidData = pet.getName() != null && !pet.getName().trim().isEmpty();
        boolean hasPhotos = pet.getPhotoUrls() != null && !pet.getPhotoUrls().isEmpty();

        boolean result = isPending && hasValidData && hasPhotos;

        logger.debug("Pet {} sale eligibility check result: {}", pet.getPetId(), result);
        return result;
    }
}
