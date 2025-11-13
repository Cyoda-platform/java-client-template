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
 * ABOUTME: Criterion that validates pet data meets quality and consistency requirements.
 * Checks for required fields, data format, and business rules.
 */
@Component
public class PetDataValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetDataValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking pet data validation criteria for request: {}", request.getId());
        
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

        // Check if entity is null (structural validation)
        if (pet == null) {
            logger.warn("Pet entity is null");
            return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if entity is valid
        if (!pet.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Pet entity is not valid - missing required fields");
            return EvaluationOutcome.fail("Pet entity is not valid - missing required fields",
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate pet name is not empty
        if (pet.getName() == null || pet.getName().trim().isEmpty()) {
            logger.warn("Pet {} has empty name", pet.getPetId());
            return EvaluationOutcome.fail("Pet name cannot be empty", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Validate species is not empty
        if (pet.getSpecies() == null || pet.getSpecies().trim().isEmpty()) {
            logger.warn("Pet {} has empty species", pet.getPetId());
            return EvaluationOutcome.fail("Pet species cannot be empty", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Validate age if provided
        if (pet.getAge() != null && pet.getAge() < 0) {
            logger.warn("Pet {} has invalid age: {}", pet.getPetId(), pet.getAge());
            return EvaluationOutcome.fail("Pet age cannot be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.debug("Pet {} passed all validation checks", pet.getPetId());
        return EvaluationOutcome.success();
    }
}

