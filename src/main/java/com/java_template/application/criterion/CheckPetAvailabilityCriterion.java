package com.java_template.application.criterion;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CheckPetAvailabilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CheckPetAvailabilityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking pet availability for request: {}", request.getId());
        
        // This is a predefined chain. Just write the business logic in validateEntity method.
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
        Pet entity = context.entity();
        
        // Check if pet entity is valid
        if (entity == null) {
            logger.warn("Pet entity is null");
            return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        if (!entity.isValid()) {
            logger.warn("Pet entity is not valid: {}", entity.getId());
            return EvaluationOutcome.fail("Pet entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        // Check if pet is available for adoption
        if (entity.getStatus() == null) {
            logger.warn("Pet status is null for pet: {}", entity.getId());
            return EvaluationOutcome.fail("Pet status is not set", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        
        String status = entity.getStatus().toUpperCase();
        
        // Pet is available if status is "AVAILABLE"
        if ("AVAILABLE".equals(status)) {
            logger.info("Pet {} ({}) is available for adoption", entity.getName(), entity.getId());
            return EvaluationOutcome.success();
        }
        
        // Pet is not available if already adopted
        if ("ADOPTED".equals(status)) {
            logger.info("Pet {} ({}) is already adopted", entity.getName(), entity.getId());
            return EvaluationOutcome.fail(
                String.format("Pet %s is already adopted", entity.getName()), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }
        
        // Pet is not available for other statuses (e.g., "PENDING", "RESERVED", etc.)
        logger.info("Pet {} ({}) is not available for adoption. Current status: {}", 
                   entity.getName(), entity.getId(), entity.getStatus());
        return EvaluationOutcome.fail(
            String.format("Pet %s is not available for adoption (status: %s)", entity.getName(), entity.getStatus()), 
            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
        );
    }
}
