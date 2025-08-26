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
public class ValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // Use serializer chain to evaluate the HNItem entity according to validation rules.
        return serializer.withRequest(request)
            .evaluateEntity(HNItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match the criterion class name exactly (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItem> context) {
         HNItem entity = context.entity();

         // defensive null-check: if entity is missing treat as validation failure
         if (entity == null) {
             String message = "Entity is null";
             logger.debug("Validation failed: {}", message);
             return EvaluationOutcome.fail(message, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         StringBuilder msg = new StringBuilder();
         boolean failed = false;

         // Validate required fields for HNItem: id and type (per functional requirements)
         if (entity.getId() == null || entity.getId() <= 0) {
             msg.append("Missing or invalid 'id'.");
             failed = true;
         }
         if (entity.getType() == null || entity.getType().isBlank()) {
             if (failed) msg.append(" ");
             msg.append("Missing or empty 'type'.");
             failed = true;
         }

         // Additional data-quality check: originalJson should be present (service stores original JSON)
         if (entity.getOriginalJson() == null || entity.getOriginalJson().isBlank()) {
             if (failed) msg.append(" ");
             msg.append("Missing or empty 'originalJson'.");
             // treat as data quality failure distinct from simple validation of id/type
             String message = msg.toString();
             logger.debug("Validation/data-quality failed for HNItem id={} type={}: {}", entity.getId(), entity.getType(), message);
             return EvaluationOutcome.fail(message, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (failed) {
             String message = msg.toString();
             logger.debug("Validation failed for HNItem id={} type={}: {}", entity.getId(), entity.getType(), message);
             return EvaluationOutcome.fail(message, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}