package com.java_template.application.criterion;

import com.java_template.application.entity.catfact.version_1.CatFact;
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
 * ABOUTME: Criterion for validating cat fact data before publishing.
 * Checks content length and required fields.
 */
@Component
public class CatFactValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private static final int MIN_CONTENT_LENGTH = 10;

    public CatFactValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking CatFact validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(CatFact.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<CatFact> context) {
        CatFact entity = context.entityWithMetadata().entity();

        if (entity == null) {
            logger.warn("CatFact is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!entity.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("CatFact is not valid");
            return EvaluationOutcome.fail("Entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate content length
        if (entity.getContent() != null && entity.getContent().length() < MIN_CONTENT_LENGTH) {
            logger.warn("Cat fact content too short for fact: {}", entity.getFactId());
            return EvaluationOutcome.fail("Content too short", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}

