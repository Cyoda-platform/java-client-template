package com.java_template.application.criterion;

import com.java_template.application.entity.importjob.version_1.ImportJob;
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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

@Component
public class SourceReachableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public SourceReachableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(ImportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name
        return "SourceReachableCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ImportJob> context) {
         ImportJob entity = context.entity();

         // Validate required job identifiers and requester
         if (entity.getJobId() == null || entity.getJobId().isBlank()) {
             logger.debug("ImportJob missing jobId");
             return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRequestedBy() == null || entity.getRequestedBy().isBlank()) {
             logger.debug("ImportJob [{}] missing requestedBy", entity.getJobId());
             return EvaluationOutcome.fail("requestedBy is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
             logger.debug("ImportJob [{}] missing createdAt", entity.getJobId());
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic presence validation for sourceUrl
         String sourceUrl = entity.getSourceUrl();
         if (sourceUrl == null || sourceUrl.isBlank()) {
             logger.debug("ImportJob [{}] failed source validation: sourceUrl is missing", entity.getJobId());
             return EvaluationOutcome.fail("sourceUrl is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate mode if provided
         String mode = entity.getMode();
         if (mode != null && !mode.isBlank()) {
             String m = mode.trim().toLowerCase();
             if (!m.equals("full") && !m.equals("incremental")) {
                 logger.debug("ImportJob [{}] has unsupported mode: {}", entity.getJobId(), mode);
                 return EvaluationOutcome.fail("unsupported mode: must be 'full' or 'incremental'", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Validate URL format and protocol
         URL url;
         try {
             url = new URL(sourceUrl);
         } catch (MalformedURLException e) {
             logger.debug("ImportJob [{}] has malformed sourceUrl: {}", entity.getJobId(), sourceUrl);
             return EvaluationOutcome.fail("sourceUrl is not a valid URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String protocol = url.getProtocol();
         if (protocol == null || !(protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https"))) {
             logger.debug("ImportJob [{}] has unsupported protocol: {}", entity.getJobId(), protocol);
             return EvaluationOutcome.fail("sourceUrl must use http or https", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (url.getHost() == null || url.getHost().isBlank()) {
             logger.debug("ImportJob [{}] has invalid host in sourceUrl: {}", entity.getJobId(), sourceUrl);
             return EvaluationOutcome.fail("sourceUrl must include a valid host", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Try to reach the source with a short timeout. Treat non-2xx/3xx responses as data quality issues.
         try {
             HttpURLConnection conn = (HttpURLConnection) url.openConnection();
             conn.setRequestMethod("HEAD");
             conn.setConnectTimeout(3000);
             conn.setReadTimeout(3000);
             conn.setInstanceFollowRedirects(true);
             conn.connect();
             int code = conn.getResponseCode();

             // Some servers may not support HEAD; treat 405 as a signal to attempt GET
             if (code == HttpURLConnection.HTTP_BAD_METHOD) {
                 conn = (HttpURLConnection) url.openConnection();
                 conn.setRequestMethod("GET");
                 conn.setConnectTimeout(3000);
                 conn.setReadTimeout(3000);
                 conn.setInstanceFollowRedirects(true);
                 conn.connect();
                 code = conn.getResponseCode();
             }

             if (code >= 200 && code < 400) {
                 // reachable
                 logger.debug("ImportJob [{}] source reachable (HTTP {})", entity.getJobId(), code);
                 return EvaluationOutcome.success();
             } else {
                 logger.warn("ImportJob [{}] source responded with HTTP {}", entity.getJobId(), code);
                 return EvaluationOutcome.fail("source returned HTTP " + code, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } catch (IOException e) {
             logger.warn("ImportJob [{}] source unreachable: {}", entity.getJobId(), e.getMessage());
             return EvaluationOutcome.fail("source unreachable: " + e.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
    }
}