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
public class ValidationPassCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationPassCriterion(SerializerFactory serializerFactory) {
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

         // Basic presence checks on the ingest job
         if (entity == null) {
             logger.warn("IngestJob entity is null in ValidationPassCriterion");
             return EvaluationOutcome.fail("IngestJob is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Map<String, Object> payload = entity.getHnPayload();
         if (payload == null) {
             return EvaluationOutcome.fail("hn_payload is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // id must be present and numeric
         Object idObj = payload.get("id");
         if (idObj == null) {
             return EvaluationOutcome.fail("hn_payload.id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!(idObj instanceof Number)) {
             return EvaluationOutcome.fail("hn_payload.id must be a number", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // by must be present and non-blank
         Object byObj = payload.get("by");
         if (byObj == null) {
             return EvaluationOutcome.fail("hn_payload.by is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!(byObj instanceof String) || ((String) byObj).isBlank()) {
             return EvaluationOutcome.fail("hn_payload.by must be a non-blank string", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // time must be present and numeric
         Object timeObj = payload.get("time");
         if (timeObj == null) {
             return EvaluationOutcome.fail("hn_payload.time is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!(timeObj instanceof Number)) {
             return EvaluationOutcome.fail("hn_payload.time must be a number (unix timestamp)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // type must be present and non-blank
         Object typeObj = payload.get("type");
         if (typeObj == null) {
             return EvaluationOutcome.fail("hn_payload.type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!(typeObj instanceof String) || ((String) typeObj).isBlank()) {
             return EvaluationOutcome.fail("hn_payload.type must be a non-blank string", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String type = (String) typeObj;

         // If type is 'story' then title must be present and non-blank
         if ("story".equalsIgnoreCase(type)) {
             Object titleObj = payload.get("title");
             if (titleObj == null) {
                 return EvaluationOutcome.fail("hn_payload.title is required for story items", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (!(titleObj instanceof String) || ((String) titleObj).isBlank()) {
                 return EvaluationOutcome.fail("hn_payload.title must be a non-blank string for story items", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}