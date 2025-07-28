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
public class IsInvalidHnItemJsonCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsInvalidHnItemJsonCriterion(SerializerFactory serializerFactory) {
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

        // This criterion should detect invalid JSON or missing mandatory fields
        if (hackerNewsItem.getRawJson() == null || hackerNewsItem.getRawJson().isBlank()) {
            return EvaluationOutcome.success(); // This is invalid, so success for the invalid criterion
        }

        if (hackerNewsItem.getId() == null || hackerNewsItem.getId() <= 0) {
            return EvaluationOutcome.success();
        }

        if (hackerNewsItem.getBy() == null || hackerNewsItem.getBy().isBlank()) {
            return EvaluationOutcome.success();
        }

        if (hackerNewsItem.getTitle() == null || hackerNewsItem.getTitle().isBlank()) {
            return EvaluationOutcome.success();
        }

        if (hackerNewsItem.getType() == null || hackerNewsItem.getType().isBlank()) {
            return EvaluationOutcome.success();
        }

        // If none of the above, then this is not invalid
        return EvaluationOutcome.fail("HackerNewsItem JSON is valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
