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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Component
public class JobValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
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
             logger.warn("JobValidationCriterion: entity is null");
             return EvaluationOutcome.fail("Job entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Retrieve properties via reflection to avoid compile-time dependency on specific getter names.
         String jobId = asString(job, "jobId", "id");
         String name = asString(job, "name");
         String status = asString(job, "status");
         String sourceEndpoint = asString(job, "sourceEndpoint", "source", "url");
         Object parametersObj = fetch(job, "parameters", "params");
         Integer retryCount = asInteger(job, "retryCount", "retries");
         String createdAt = asString(job, "createdAt", "created");
         String lastRunAt = asString(job, "lastRunAt", "lastRun", "last_run_at");
         String resultSummary = asString(job, "resultSummary", "result");
         String schedule = asString(job, "schedule");

         // Required string fields
         if (jobId == null || jobId.isBlank()) {
             return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (name == null || name.isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (sourceEndpoint == null || sourceEndpoint.isBlank()) {
             return EvaluationOutcome.fail("sourceEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate sourceEndpoint is a well-formed HTTP/HTTPS URI
         String source = sourceEndpoint;
         try {
             URI uri = URI.create(source);
             String scheme = uri.getScheme();
             if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                 return EvaluationOutcome.fail("sourceEndpoint must be a valid http/https URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (IllegalArgumentException ex) {
             return EvaluationOutcome.fail("sourceEndpoint must be a valid URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Parameters map must be present (can be empty)
         if (parametersObj == null) {
             return EvaluationOutcome.fail("parameters map must be present (can be empty)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         } else {
             if (!(parametersObj instanceof Map)) {
                 return EvaluationOutcome.fail("parameters must be a map", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // retryCount must be non-null and non-negative
         if (retryCount == null) {
             return EvaluationOutcome.fail("retryCount must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (retryCount < 0) {
             return EvaluationOutcome.fail("retryCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Validate timestamps if provided (createdAt and lastRunAt) are ISO-8601 parseable
         if (createdAt != null && !createdAt.isBlank()) {
             try {
                 Instant.parse(createdAt);
             } catch (DateTimeParseException e) {
                 return EvaluationOutcome.fail("createdAt must be ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }
         if (lastRunAt != null && !lastRunAt.isBlank()) {
             try {
                 Instant.parse(lastRunAt);
             } catch (DateTimeParseException e) {
                 return EvaluationOutcome.fail("lastRunAt must be ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rule: If status is RUNNING or VALIDATING then lastRunAt should be present
         if (status != null && (status.equalsIgnoreCase("RUNNING") || status.equalsIgnoreCase("VALIDATING"))) {
             if (lastRunAt == null || lastRunAt.isBlank()) {
                 return EvaluationOutcome.fail("lastRunAt must be set when status is RUNNING or VALIDATING", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Business rule: If status is COMPLETED then resultSummary should be present and non-blank
         if (status != null && status.equalsIgnoreCase("COMPLETED")) {
             if (resultSummary == null || resultSummary.isBlank()) {
                 return EvaluationOutcome.fail("resultSummary must be present when status is COMPLETED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Optional checks: resultSummary if present must not be blank
         if (resultSummary != null && resultSummary.isBlank()) {
             return EvaluationOutcome.fail("resultSummary, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // schedule (optional) - if provided must not be blank
         if (schedule != null && schedule.isBlank()) {
             return EvaluationOutcome.fail("schedule, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }

    private Object fetch(Job job, String... propertyNames) {
        if (job == null) return null;
        Class<?> cls = job.getClass();
        for (String prop : propertyNames) {
            if (prop == null || prop.isBlank()) continue;
            // Try getter: getX
            String capitalized = capitalize(prop);
            String getName = "get" + capitalized;
            try {
                Method m = cls.getMethod(getName);
                Object val = m.invoke(job);
                if (val != null) return val;
            } catch (Exception ignored) {
            }
            // Try isX for boolean
            String isName = "is" + capitalized;
            try {
                Method m = cls.getMethod(isName);
                Object val = m.invoke(job);
                if (val != null) return val;
            } catch (Exception ignored) {
            }
            // Try method with exact property name
            try {
                Method m = cls.getMethod(prop);
                Object val = m.invoke(job);
                if (val != null) return val;
            } catch (Exception ignored) {
            }
            // Try field access
            try {
                Field f = cls.getDeclaredField(prop);
                f.setAccessible(true);
                Object val = f.get(job);
                if (val != null) return val;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String asString(Job job, String... propertyNames) {
        Object o = fetch(job, propertyNames);
        return o == null ? null : o.toString();
    }

    private Integer asInteger(Job job, String... propertyNames) {
        Object o = fetch(job, propertyNames);
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}