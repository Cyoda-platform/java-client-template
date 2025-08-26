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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

         // Helper retrievals use reflection to avoid compile-time coupling to specific getter names.
         String jobId = getString(job, "getJobId", "getId", "getJobID");
         if (jobId == null || jobId.isBlank()) {
             return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String name = getString(job, "getName", "getJobName", "getFullName");
         if (name == null || name.isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String sourceEndpoint = getString(job, "getSourceEndpoint", "getSource", "getEndpoint", "getSourceUrl", "getSourceURI");
         if (sourceEndpoint == null || sourceEndpoint.isBlank()) {
             return EvaluationOutcome.fail("sourceEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Object parameters = getObject(job, "getParameters", "getParams", "getPayload", "getParametersMap");
         if (parameters == null) {
             return EvaluationOutcome.fail("parameters must be provided (can be empty)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Integer retryCount = getInteger(job, "getRetryCount", "getRetries", "getRetry");
         if (retryCount == null) {
             return EvaluationOutcome.fail("retryCount is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (retryCount < 0) {
             return EvaluationOutcome.fail("retryCount must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate sourceEndpoint syntactically (ensure valid URL and http(s) protocol)
         String endpoint = sourceEndpoint;
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
         if (retryCount > 10) {
             return EvaluationOutcome.fail("retryCount exceeds maximum allowed (10)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         String status = getString(job, "getStatus", "getState");
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
         String schedule = getString(job, "getSchedule", "getCron", "getCronExpression", "getCronExpr");
         if (schedule != null) {
             if (schedule.isBlank()) {
                 return EvaluationOutcome.fail("schedule, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             String[] parts = schedule.trim().split("\\s+");
             if (parts.length < 5 || parts.length > 7) {
                 return EvaluationOutcome.fail("schedule must be a cron-like expression with 5 to 7 fields", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         return EvaluationOutcome.success();
    }

    private Object getObject(Job job, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = job.getClass().getMethod(name);
                if (m != null) {
                    Object val = m.invoke(job);
                    if (val != null) {
                        return val;
                    }
                }
            } catch (NoSuchMethodException ignored) {
                // try next
            } catch (IllegalAccessException | InvocationTargetException e) {
                logger.debug("Error invoking method {} on Job: {}", name, e.getMessage());
            }
        }
        return null;
    }

    private String getString(Job job, String... methodNames) {
        Object o = getObject(job, methodNames);
        return o == null ? null : o.toString();
    }

    private Integer getInteger(Job job, String... methodNames) {
        Object o = getObject(job, methodNames);
        if (o == null) return null;
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            logger.debug("Unable to parse integer from value '{}' for job: {}", o, e.getMessage());
            return null;
        }
    }
}