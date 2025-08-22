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

@Component
public class ReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReadyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(HNItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItem> context) {
         HNItem entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("HNItem entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getId() == null) {
             return EvaluationOutcome.fail("Missing required field: id", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getBy() == null || entity.getBy().isBlank()) {
             return EvaluationOutcome.fail("Missing required field: by (author)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getTime() == null) {
             return EvaluationOutcome.fail("Missing required field: time (timestamp)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getType() == null || entity.getType().isBlank()) {
             return EvaluationOutcome.fail("Missing required field: type", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // For 'story' items, title is required
         if ("story".equalsIgnoreCase(entity.getType())) {
             if (entity.getTitle() == null || entity.getTitle().isBlank()) {
                 return EvaluationOutcome.fail("Missing required field: title for story item", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Basic data quality check: if rawJson is missing, warn as data quality failure
         if (entity.getRawJson() == null || entity.getRawJson().isBlank()) {
             return EvaluationOutcome.fail("Missing rawJson payload for fidelity", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed: ready for persist
         return EvaluationOutcome.success();
    }
}