package com.example.application.criterion;

import com.example.application.entity.example_entity.version_1.ExampleEntity;
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
 * Golden Example Criterion - Template for creating new criteria

 * This is a generified example criterion that demonstrates:
 * - Proper CyodaCriterion implementation
 * - Entity validation and business rule checking
 * - Proper use of EvaluationOutcome (success/fail)
 * - Appropriate reason categories for failures
 * - Error handling and logging
 * - Performance considerations

 * To create a new criterion:
 * 1. Copy this file to your criterion package
 * 2. Rename class from ExampleEntityCriterion to YourCriterionName
 * 3. Update entity type from ExampleEntity to your entity
 * 4. Implement your specific validation logic in validateEntity()
 * 5. Update supports() method to match your criterion name
 * 6. Choose appropriate StandardEvalReasonCategories for failures
 * 7. Add meaningful error messages for business users
 */
@Component
public class ExampleEntityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ExampleEntityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ExampleEntity criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(ExampleEntity.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the entity
     * This method contains all business rule validations

     * Available StandardEvalReasonCategories:
     * - STRUCTURAL_FAILURE: For null entities, missing required fields
     * - BUSINESS_RULE_FAILURE: For business logic violations
     * - DATA_QUALITY_FAILURE: For data consistency issues
     * - EXTERNAL_DEPENDENCY_FAILURE: For external service issues
     * - CONFIGURATION_FAILURE: For configuration problems
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ExampleEntity> context) {
        ExampleEntity entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("ExampleEntity is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!entity.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("ExampleEntity is not valid");
            return EvaluationOutcome.fail("Entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
