package com.java_template.application.criterion;

import com.java_template.application.entity.hnitem.version_1.HNItem;
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
 * ValidItemCriterion - Checks if HNItem data is valid
 */
@Component
public class ValidItemCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidItemCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ValidItemCriterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(HNItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the HNItem entity
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItem> context) {
        HNItem entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("HNItem is null");
            return EvaluationOutcome.fail("HNItem entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if ID is present
        if (entity.getId() == null) {
            logger.warn("HNItem ID is null");
            return EvaluationOutcome.fail("HNItem ID is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if type is present
        if (entity.getType() == null || entity.getType().trim().isEmpty()) {
            logger.warn("HNItem type is null or empty");
            return EvaluationOutcome.fail("HNItem type is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Use the entity's built-in validation
        if (!entity.isValid()) {
            logger.warn("HNItem failed validation: ID={}, Type={}", entity.getId(), entity.getType());
            return EvaluationOutcome.fail("HNItem failed validation checks", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("HNItem {} passed validation", entity.getId());
        return EvaluationOutcome.success();
    }
}
