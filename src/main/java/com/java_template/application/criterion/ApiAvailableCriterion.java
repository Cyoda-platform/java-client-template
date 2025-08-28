package com.java_template.application.criterion;

import com.java_template.application.entity.job.version_1.Job;
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

import java.time.OffsetDateTime;

@Component
public class ApiAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ApiAvailableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         // Validate presence of sourceUrl
         if (job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
             return EvaluationOutcome.fail("sourceUrl is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic URL scheme validation (we don't perform network calls here)
         String src = job.getSourceUrl().trim().toLowerCase();
         if (!(src.startsWith("http://") || src.startsWith("https://"))) {
             return EvaluationOutcome.fail("sourceUrl must use http or https scheme", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Ensure job is in SCHEDULED state before attempting to start ingestion
         if (job.getStatus() != null && !job.getStatus().isBlank() && !"SCHEDULED".equalsIgnoreCase(job.getStatus())) {
             return EvaluationOutcome.fail("job must be in SCHEDULED state to check API availability", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Validate scheduledAt is a valid ISO-8601 datetime if present
         if (job.getScheduledAt() == null || job.getScheduledAt().isBlank()) {
             return EvaluationOutcome.fail("scheduledAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         } else {
             try {
                 OffsetDateTime.parse(job.getScheduledAt());
             } catch (Exception ex) {
                 logger.debug("Invalid scheduledAt format for job {}: {}", job.getJobId(), job.getScheduledAt(), ex);
                 return EvaluationOutcome.fail("scheduledAt must be an ISO-8601 datetime", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

        return EvaluationOutcome.success();
    }
}