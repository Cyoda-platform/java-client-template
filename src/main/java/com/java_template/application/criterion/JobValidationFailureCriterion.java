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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

@Component
public class JobValidationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobValidationFailureCriterion(SerializerFactory serializerFactory) {
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
        // MUST use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             logger.warn("Job entity is null in context");
             return EvaluationOutcome.fail("Job is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields
         if (job.getJobId() == null || job.getJobId().isBlank()) {
             return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getName() == null || job.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getSourceEndpoint() == null || job.getSourceEndpoint().isBlank()) {
             return EvaluationOutcome.fail("sourceEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getParameters() == null) {
             return EvaluationOutcome.fail("parameters must be provided (can be empty)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getRetryCount() == null) {
             return EvaluationOutcome.fail("retryCount is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getRetryCount() < 0) {
             return EvaluationOutcome.fail("retryCount must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate sourceEndpoint syntactically (ensure valid URL and http(s) protocol)
         String endpoint = job.getSourceEndpoint();
         try {
             URL u = new URL(endpoint);
             String protocol = u.getProtocol();
             if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                 return EvaluationOutcome.fail("sourceEndpoint must use http or https", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (u.getHost() == null || u.getHost().isBlank()) {
                 return EvaluationOutcome.fail("sourceEndpoint must contain a valid host", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } catch (MalformedURLException e) {
             return EvaluationOutcome.fail("sourceEndpoint is not a valid URL", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rules: enforce sensible retry limits and known status values
         if (job.getRetryCount() > 10) {
             return EvaluationOutcome.fail("retryCount exceeds maximum allowed (10)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         String status = job.getStatus();
         if (status != null && !status.isBlank()) {
             Set<String> allowed = Set.of(
                 "CREATED", "VALIDATING", "RUNNING", "ANALYZING",
                 "COMPLETED", "FAILED", "NOTIFICATION_QUEUED", "ARCHIVED"
             );
             if (!allowed.contains(status)) {
                 return EvaluationOutcome.fail("status contains unsupported value", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // schedule is optional but if provided it must not be blank and should resemble a cron expression (5-7 fields)
         if (job.getSchedule() != null) {
             if (job.getSchedule().isBlank()) {
                 return EvaluationOutcome.fail("schedule, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             String[] parts = job.getSchedule().trim().split("\\s+");
             if (parts.length < 5 || parts.length > 7) {
                 return EvaluationOutcome.fail("schedule must be a cron-like expression with 5 to 7 fields", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         return EvaluationOutcome.success();
    }
}