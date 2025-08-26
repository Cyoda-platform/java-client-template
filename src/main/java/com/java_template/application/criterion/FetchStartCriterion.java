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
import java.net.URISyntaxException;

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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(IngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match criterion name exactly
        return "FetchStartCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob entity = context.entity();

         if (entity == null) {
             logger.warn("IngestionJob entity is null in FetchStartCriterion");
             return EvaluationOutcome.fail("Entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: sourceEndpoint
         String source = entity.getSourceEndpoint();
         if (source == null || source.isBlank()) {
             return EvaluationOutcome.fail("sourceEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate sourceEndpoint is a valid http/https URL
         try {
             URI uri = new URI(source);
             String scheme = uri.getScheme();
             if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                 return EvaluationOutcome.fail("sourceEndpoint must use http or https", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (URISyntaxException e) {
             return EvaluationOutcome.fail("sourceEndpoint is not a valid URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: schedule
         String schedule = entity.getSchedule();
         if (schedule == null || schedule.isBlank()) {
             return EvaluationOutcome.fail("schedule is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic cron expression sanity check: at least 5 space-separated fields
         String[] parts = schedule.trim().split("\\s+");
         if (parts.length < 5) {
             return EvaluationOutcome.fail("schedule must be a cron expression with at least 5 fields", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // initiatedBy should be present
         String initiatedBy = entity.getInitiatedBy();
         if (initiatedBy == null || initiatedBy.isBlank()) {
             return EvaluationOutcome.fail("initiatedBy is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // processedCount if present must be non-negative (entity.isValid also enforces this but include defensive check)
         Integer processed = entity.getProcessedCount();
         if (processed != null && processed < 0) {
             return EvaluationOutcome.fail("processedCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If all checks pass, allow fetch start
         return EvaluationOutcome.success();
    }
}