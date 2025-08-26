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
public class ValidationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailureCriterion(SerializerFactory serializerFactory) {
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
        // MUST use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob entity = context.entity();
         if (entity == null) {
             logger.warn("ValidationFailureCriterion: entity is null in context");
             return EvaluationOutcome.fail("Entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Check required fields per functional requirements: sourceEndpoint and schedule must be present and valid.
         String sourceEndpoint = entity.getSourceEndpoint();
         if (sourceEndpoint == null || sourceEndpoint.isBlank()) {
             return EvaluationOutcome.fail("sourceEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Basic URL validation: must be a valid URI and use http or https
         try {
             URI uri = new URI(sourceEndpoint.trim());
             String scheme = uri.getScheme();
             if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                 return EvaluationOutcome.fail("sourceEndpoint must be a valid http(s) URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (Exception ex) {
             return EvaluationOutcome.fail("sourceEndpoint is not a valid URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String schedule = entity.getSchedule();
         if (schedule == null || schedule.isBlank()) {
             return EvaluationOutcome.fail("schedule is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Basic cron-like validation: require at least 5 space-separated fields (covers common cron and simple quartz expressions)
         int scheduleParts = schedule.trim().split("\\s+").length;
         if (scheduleParts < 5) {
             return EvaluationOutcome.fail("schedule appears invalid (expected cron-like expression with >=5 fields)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Check initiatedBy (should be present)
         String initiatedBy = entity.getInitiatedBy();
         if (initiatedBy == null || initiatedBy.isBlank()) {
             return EvaluationOutcome.fail("initiatedBy is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Leverage entity-level validity for other checks (processedCount, startedAt/finishedAt rules etc.)
         // If entity.isValid() fails, provide a generic validation failure reason.
         try {
             if (!entity.isValid()) {
                 return EvaluationOutcome.fail("entity failed domain validation checks", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (Exception e) {
             logger.warn("Exception while invoking entity.isValid(): {}", e.getMessage());
             return EvaluationOutcome.fail("entity validation encountered an error", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // All validations passed
         return EvaluationOutcome.success();
    }
}