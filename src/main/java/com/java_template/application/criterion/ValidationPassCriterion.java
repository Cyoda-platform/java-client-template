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
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(IngestJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestJob> context) {
         IngestJob entity = context.entity();

         if (entity == null) {
             logger.warn("IngestJob entity is null");
             return EvaluationOutcome.fail("ingest job missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required fields on the job
         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("created_at is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Map<String, Object> hnPayload = entity.getHnPayload();
         if (hnPayload == null) {
             return EvaluationOutcome.fail("hn_payload is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate core Hacker News fields expected in the payload
         Object idObj = hnPayload.get("id");
         if (idObj == null) {
             return EvaluationOutcome.fail("hn_payload.id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!(idObj instanceof Number)) {
             return EvaluationOutcome.fail("hn_payload.id must be a number", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Object byObj = hnPayload.get("by");
         if (byObj == null || !(byObj instanceof String) || ((String) byObj).isBlank()) {
             return EvaluationOutcome.fail("hn_payload.by is required and must be non-blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Object timeObj = hnPayload.get("time");
         if (timeObj == null) {
             return EvaluationOutcome.fail("hn_payload.time is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!(timeObj instanceof Number)) {
             return EvaluationOutcome.fail("hn_payload.time must be a number (unix timestamp)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // type/title rules: for story-type items, title must be present
         Object typeObj = hnPayload.get("type");
         String typeStr = null;
         if (typeObj instanceof String) {
             typeStr = (String) typeObj;
         }
         if ("story".equalsIgnoreCase(typeStr)) {
             Object titleObj = hnPayload.get("title");
             if (titleObj == null || !(titleObj instanceof String) || ((String) titleObj).isBlank()) {
                 return EvaluationOutcome.fail("hn_payload.title is required for story items", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Optional sanity check: if url present it should be a non-blank string
         Object urlObj = hnPayload.get("url");
         if (urlObj != null && !(urlObj instanceof String)) {
             return EvaluationOutcome.fail("hn_payload.url must be a string when present", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If all checks passed, success
         return EvaluationOutcome.success();
    }
}