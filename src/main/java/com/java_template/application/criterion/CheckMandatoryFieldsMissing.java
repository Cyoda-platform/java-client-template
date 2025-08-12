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
public class CheckMandatoryFieldsMissing implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CheckMandatoryFieldsMissing(SerializerFactory serializerFactory) {
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
         HackerNewsItem entity = context.entity();
         // Implement validation logic based on business requirements
         boolean idMissing = entity.getId() == null;
         boolean typeMissing = entity.getType() == null || entity.getType().isEmpty();

         if (idMissing && typeMissing) {
             return EvaluationOutcome.fail("Missing mandatory fields: id, type", StandardEvalReasonCategories.VALIDATION_FAILURE);
         } else if (idMissing) {
             return EvaluationOutcome.fail("Missing mandatory field: id", StandardEvalReasonCategories.VALIDATION_FAILURE);
         } else if (typeMissing) {
             return EvaluationOutcome.fail("Missing mandatory field: type", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // If mandatory fields are present, this criterion is considered failing to indicate transition to INVALID
         // but logically this criterion checks for missing fields, so success means no missing fields
         return EvaluationOutcome.success();
    }
}
