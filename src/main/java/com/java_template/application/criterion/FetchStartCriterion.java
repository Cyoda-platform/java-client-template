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

import java.net.URI;

@Component
public class FetchStartCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FetchStartCriterion(SerializerFactory serializerFactory) {
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
        // must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob job = context.entity();
         if (job == null) {
             logger.warn("FetchStartCriterion: ingestion job entity is null");
             return EvaluationOutcome.fail("Ingestion job is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required: sourceEndpoint must be present and a valid URI
         String sourceEndpoint = job.getSourceEndpoint();
         if (sourceEndpoint == null || sourceEndpoint.isBlank()) {
             return EvaluationOutcome.fail("sourceEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         try {
             URI uri = new URI(sourceEndpoint.trim());
             if (uri.getScheme() == null || uri.getHost() == null) {
                 return EvaluationOutcome.fail("sourceEndpoint must be a valid URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (Exception e) {
             return EvaluationOutcome.fail("sourceEndpoint must be a valid URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: schedule must be present and appear like a cron expression (basic heuristic)
         String schedule = job.getSchedule();
         if (schedule == null || schedule.isBlank()) {
             return EvaluationOutcome.fail("schedule is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String[] parts = schedule.trim().split("\\s+");
         if (parts.length < 5) {
             // Many cron formats use 5 or more fields; this is a best-effort check without adding dependencies
             return EvaluationOutcome.fail("schedule does not appear to be a valid CRON expression", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: prevent triggering fetch if it has already been started
         String startedAt = job.getStartedAt();
         if (startedAt != null && !startedAt.isBlank()) {
             return EvaluationOutcome.fail("fetch already started for this job", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // processedCount should not be negative (entity validation already enforces this, but double-check)
         Integer processedCount = job.getProcessedCount();
         if (processedCount != null && processedCount < 0) {
             return EvaluationOutcome.fail("processedCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}