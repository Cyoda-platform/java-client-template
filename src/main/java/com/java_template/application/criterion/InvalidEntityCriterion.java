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
public class InvalidEntityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public InvalidEntityCriterion(SerializerFactory serializerFactory) {
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

        // Validate invalid conditions: any of the required fields missing or blank
        if (hackerNewsItem.getHnId() == null) {
            return EvaluationOutcome.success(); // This criterion means entity is invalid if hnId is missing
        }
        if (hackerNewsItem.getBy() == null || hackerNewsItem.getBy().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (hackerNewsItem.getType() == null || hackerNewsItem.getType().isBlank()) {
            return EvaluationOutcome.success();
        }
        if (hackerNewsItem.getTime() == null) {
            return EvaluationOutcome.success();
        }

        // If all required fields are present, then entity is not invalid
        return EvaluationOutcome.fail("Entity is valid, does not meet invalid criteria", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
