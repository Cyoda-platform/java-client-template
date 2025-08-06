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
public class ValidateMandatoryFieldsNegative implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateMandatoryFieldsNegative(SerializerFactory serializerFactory) {
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

        // Negative validation logic - succeeds when entity is invalid
        // This criterion should pass when mandatory fields are missing

        boolean hasValidId = hackerNewsItem.getId() != null;
        boolean hasValidType = hackerNewsItem.getType() != null && !hackerNewsItem.getType().isBlank();
        boolean hasValidItem = hackerNewsItem.getItem() != null;

        boolean hasValidJsonId = hasValidItem && hackerNewsItem.getItem().hasNonNull("id");
        boolean hasValidJsonType = hasValidItem &&
            hackerNewsItem.getItem().hasNonNull("type") &&
            !hackerNewsItem.getItem().get("type").asText().isBlank();

        // If all validations pass, this negative criterion should fail
        if (hasValidId && hasValidType && hasValidItem && hasValidJsonId && hasValidJsonType) {
            return EvaluationOutcome.fail("Entity is valid, negative validation should fail", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // If any validation fails, this negative criterion succeeds
        return EvaluationOutcome.success();
    }
}
