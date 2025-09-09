package com.java_template.application.criterion;

import com.java_template.application.entity.pet_entity.version_1.PetEntity;
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
 * PetDataValidityCriterion - Check if extracted pet data is valid for processing
 * Transition: validate_pet_data (extracted → validated)
 */
@Component
public class PetDataValidityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetDataValidityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking PetDataValidity criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(PetEntity.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validate that essential pet data fields are present and valid
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetEntity> context) {
        PetEntity entity = context.entityWithMetadata().entity();

        // Check if entity is null
        if (entity == null) {
            logger.warn("PetEntity is null");
            return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if pet ID is present
        if (entity.getPetId() == null) {
            logger.warn("Pet ID is null for entity");
            return EvaluationOutcome.fail("Pet ID is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if name is present and not empty
        if (entity.getName() == null || entity.getName().trim().isEmpty()) {
            logger.warn("Pet name is null or empty for petId: {}", entity.getPetId());
            return EvaluationOutcome.fail("Pet name is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if category is present
        if (entity.getCategory() == null) {
            logger.warn("Pet category is null for petId: {}", entity.getPetId());
            return EvaluationOutcome.fail("Pet category is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if price is valid (if present)
        if (entity.getPrice() != null && entity.getPrice() < 0) {
            logger.warn("Pet price is negative for petId: {}", entity.getPetId());
            return EvaluationOutcome.fail("Pet price cannot be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        logger.debug("Pet data validation passed for petId: {}", entity.getPetId());
        return EvaluationOutcome.success();
    }
}
