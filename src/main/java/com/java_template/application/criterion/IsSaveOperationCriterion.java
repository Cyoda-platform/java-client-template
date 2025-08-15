package com.java_template.application.criterion;

import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
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

@Component
public class IsSaveOperationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsSaveOperationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Evaluating IsSaveOperationCriterion for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(HackerNewsItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();
        if (entity == null) {
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        String idStr = entity.getId();
        String type = entity.getType();
        if (idStr == null || idStr.isBlank()) {
            return EvaluationOutcome.fail("Missing id", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        try {
            long id = Long.parseLong(idStr);
            if (id < 0) {
                return EvaluationOutcome.fail("id must be >= 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (NumberFormatException e) {
            return EvaluationOutcome.fail("id must be an integer", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (type == null || type.isBlank()) {
            return EvaluationOutcome.fail("Missing type", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Optionally warn on unknown types but allow them: return success
        return EvaluationOutcome.success();
    }
}
