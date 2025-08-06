package com.java_template.application.criterion;

import com.java_template.application.entity.HackerNewsItem;
import com.java_template.common.serializer.*;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ValidateMandatoryFields implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateMandatoryFields(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request)
            .evaluateEntity(HackerNewsItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HackerNewsItem> context) {

        HackerNewsItem hackerNewsItem = context.entity();

        // Validation logic based on business requirements from prototype

        if (hackerNewsItem.getId() == null) {
            return EvaluationOutcome.fail("Field 'id' is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (hackerNewsItem.getType() == null || hackerNewsItem.getType().isBlank()) {
            return EvaluationOutcome.fail("Field 'type' is required and cannot be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate that item JSON data is present
        if (hackerNewsItem.getItem() == null) {
            return EvaluationOutcome.fail("Item JSON data is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate that the JSON item contains the required id and type fields
        if (!hackerNewsItem.getItem().hasNonNull("id")) {
            return EvaluationOutcome.fail("JSON item must contain 'id' field", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (!hackerNewsItem.getItem().hasNonNull("type") ||
            hackerNewsItem.getItem().get("type").asText().isBlank()) {
            return EvaluationOutcome.fail("JSON item must contain non-blank 'type' field", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
