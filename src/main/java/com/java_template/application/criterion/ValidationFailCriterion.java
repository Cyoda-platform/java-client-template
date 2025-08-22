package com.java_template.application.criterion;

import com.java_template.application.entity.ingestjob.version_1.IngestJob;
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

import java.util.Map;

@Component
public class ValidationFailCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(IngestJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestJob> context) {
         IngestJob entity = context.entity();

         Map<String, Object> hn = entity.getHnPayload();
         if (hn == null) {
             return EvaluationOutcome.fail("hn_payload is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // id
         Object id = hn.get("id");
         if (id == null) {
             return EvaluationOutcome.fail("hn_payload.id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // by (author)
         Object by = hn.get("by");
         if (by == null) {
             return EvaluationOutcome.fail("hn_payload.by is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (by instanceof String && ((String) by).isBlank()) {
             return EvaluationOutcome.fail("hn_payload.by must be non-blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // time
         Object time = hn.get("time");
         if (time == null) {
             return EvaluationOutcome.fail("hn_payload.time is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // title (HNItem entity requires title)
         Object title = hn.get("title");
         if (title == null) {
             return EvaluationOutcome.fail("hn_payload.title is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (title instanceof String && ((String) title).isBlank()) {
             return EvaluationOutcome.fail("hn_payload.title must be non-blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Optionally validate type if present (ensure it's a non-blank string)
         Object type = hn.get("type");
         if (type != null && type instanceof String && ((String) type).isBlank()) {
             return EvaluationOutcome.fail("hn_payload.type must be non-blank when provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}