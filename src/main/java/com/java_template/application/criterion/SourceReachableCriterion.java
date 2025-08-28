package com.java_template.application.criterion;

import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class SourceReachableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SourceReachableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetIngestionJob> context) {
         PetIngestionJob job = context.entity();
         if (job == null) {
             logger.warn("PetIngestionJob entity is null in SourceReachableCriterion");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String jobName = getStringProperty(job, "jobName");
         String sourceUrl = getStringProperty(job, "sourceUrl");

         if (sourceUrl == null || sourceUrl.isBlank()) {
             logger.debug("PetIngestionJob {} has no sourceUrl", jobName != null ? jobName : "<unknown>");
             return EvaluationOutcome.fail("sourceUrl is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String lower = sourceUrl.toLowerCase();
         if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
             logger.debug("PetIngestionJob {} has invalid sourceUrl: {}", jobName != null ? jobName : "<unknown>", sourceUrl);
             return EvaluationOutcome.fail("sourceUrl must be a valid http/https URL", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic defensive checks: if job already marked FAILED, surface as business rule failure
         String status = null;
         try {
             Method statusMethod = job.getClass().getMethod("getStatus");
             Object statusObj = statusMethod.invoke(job);
             if (statusObj != null) status = String.valueOf(statusObj);
         } catch (Exception ignored) {
             // If getStatus doesn't exist or fails, leave status as null and continue
         }

         if (status != null && status.equalsIgnoreCase("FAILED")) {
             logger.debug("PetIngestionJob {} is already FAILED", jobName != null ? jobName : "<unknown>");
             return EvaluationOutcome.fail("Job is marked as FAILED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Attempt a lightweight network check (HEAD) to verify reachability and basic responsiveness.
         try {
             HttpClient client = HttpClient.newBuilder()
                 .followRedirects(HttpClient.Redirect.NORMAL)
                 .connectTimeout(Duration.ofSeconds(5))
                 .build();

             HttpRequest req = HttpRequest.newBuilder()
                 .uri(URI.create(sourceUrl))
                 .timeout(Duration.ofSeconds(5))
                 // Use HEAD to avoid downloading large payloads; fallback to GET if server doesn't support HEAD.
                 .method("HEAD", HttpRequest.BodyPublishers.noBody())
                 .build();

             HttpResponse<Void> resp;
             try {
                 resp = client.send(req, HttpResponse.BodyHandlers.discarding());
             } catch (UnsupportedOperationException | IOException | InterruptedException headEx) {
                 // Try GET as fallback for servers that reject HEAD
                 HttpRequest getReq = HttpRequest.newBuilder()
                     .uri(URI.create(sourceUrl))
                     .timeout(Duration.ofSeconds(5))
                     .GET()
                     .build();
                 resp = client.send(getReq, HttpResponse.BodyHandlers.discarding());
             }

             int code = resp.statusCode();
             if (code >= 200 && code < 400) {
                 // reachable
                 return EvaluationOutcome.success();
             } else {
                 logger.warn("PetIngestionJob {} sourceUrl responded with status {} for URL {}", jobName != null ? jobName : "<unknown>", code, sourceUrl);
                 return EvaluationOutcome.fail("sourceUrl not reachable (HTTP " + code + ")", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } catch (IOException | InterruptedException ex) {
             Thread.currentThread().interrupt();
             logger.warn("PetIngestionJob {} sourceUrl check failed: {} - {}", jobName != null ? jobName : "<unknown>", ex.getClass().getSimpleName(), ex.getMessage());
             return EvaluationOutcome.fail("sourceUrl not reachable: " + ex.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         } catch (Exception ex) {
             logger.warn("Unexpected error while checking sourceUrl for job {}: {}", jobName != null ? jobName : "<unknown>", ex.getMessage(), ex);
             return EvaluationOutcome.fail("Unexpected error checking sourceUrl: " + ex.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
    }

    private String getStringProperty(Object obj, String property) {
        if (obj == null || property == null || property.isBlank()) return null;
        Class<?> cls = obj.getClass();
        String cap = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        String[] methodNames = new String[] { "get" + cap, property, "is" + cap };
        for (String mName : methodNames) {
            try {
                Method m = cls.getMethod(mName);
                Object val = m.invoke(obj);
                if (val != null) return String.valueOf(val);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                logger.debug("Error invoking method {} on {}: {}", mName, cls.getName(), e.getMessage());
            }
        }
        try {
            Field f = cls.getDeclaredField(property);
            f.setAccessible(true);
            Object val = f.get(obj);
            if (val != null) return String.valueOf(val);
        } catch (NoSuchFieldException ignored) {
        } catch (Exception e) {
            logger.debug("Error accessing field {} on {}: {}", property, cls.getName(), e.getMessage());
        }
        return null;
    }
}