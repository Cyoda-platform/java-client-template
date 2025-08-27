package com.java_template.application.criterion;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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
public class FetchCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FetchCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(IngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        if (modelSpec == null || modelSpec.operationName() == null) {
            return false;
        }
        // Use exact match for criterion name as required
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob entity = context.entity();

         if (entity == null) {
             logger.warn("IngestionJob entity is null in FetchCompleteCriterion");
             return EvaluationOutcome.fail("Ingestion job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // status must be present
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Ingestion job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // This criterion guards the transition from FETCHING -> TRANSFORMING.
         // It should only succeed if the job is currently in FETCHING state.
         if (!"FETCHING".equals(entity.getStatus())) {
             return EvaluationOutcome.fail(
                 String.format("Ingestion job is not in FETCHING state (current: %s)", entity.getStatus()),
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
             );
         }

         // Basic required fields verification (use only existing properties)
         if (entity.getSourceUrl() == null || entity.getSourceUrl().isBlank()) {
             return EvaluationOutcome.fail("sourceUrl is required for fetch completion", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic syntactic validation for sourceUrl: must start with http:// or https://
         String src = entity.getSourceUrl().trim();
         if (!(src.startsWith("http://") || src.startsWith("https://"))) {
             return EvaluationOutcome.fail("sourceUrl must be a valid http(s) URL", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (entity.getDataFormats() == null || entity.getDataFormats().isBlank()) {
             return EvaluationOutcome.fail("dataFormats is required for fetch completion", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Ensure at least one supported data format is specified (JSON or XML)
         String formats = entity.getDataFormats().toUpperCase();
         boolean supportsJson = formats.contains("JSON");
         boolean supportsXml = formats.contains("XML");
         if (!supportsJson && !supportsXml) {
             return EvaluationOutcome.fail("Unsupported data formats; expected JSON and/or XML", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (entity.getTimeWindowDays() == null || entity.getTimeWindowDays() < 0) {
             return EvaluationOutcome.fail("timeWindowDays must be a non-negative integer", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Optional: ensure creator metadata exists to help trace the job (use existing properties)
         if (entity.getCreatedBy() == null || entity.getCreatedBy().isBlank()) {
             return EvaluationOutcome.fail("createdBy is required for ingestion job traceability", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt timestamp is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All basic checks passed — consider fetch complete and allow transition to TRANSFORMING.
         logger.debug("FetchCompleteCriterion passed for IngestionJob id={}", entity.getId());
         return EvaluationOutcome.success();
    }
}