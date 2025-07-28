package com.java_template.application.criterion;

import com.java_template.application.entity.HackerNewsItem;
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
public class ValidEntityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidEntityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request)
                .evaluateEntity(HackerNewsItem.class, this::validateEntity)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HackerNewsItem> context) {

        HackerNewsItem hackerNewsItem = context.entity();

        // Validate required fields: hnId, by, type, time
        if (hackerNewsItem.getHnId() == null) {
            return EvaluationOutcome.fail("hnId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (hackerNewsItem.getBy() == null || hackerNewsItem.getBy().isBlank()) {
            return EvaluationOutcome.fail("by (author username) is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (hackerNewsItem.getType() == null || hackerNewsItem.getType().isBlank()) {
            return EvaluationOutcome.fail("type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (hackerNewsItem.getTime() == null) {
            return EvaluationOutcome.fail("time is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // If all required fields are present, success
        return EvaluationOutcome.success();
    }
}
