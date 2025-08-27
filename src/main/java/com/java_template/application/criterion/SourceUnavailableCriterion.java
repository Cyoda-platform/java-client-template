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

import java.net.URI;
import java.net.URISyntaxException;

@Component
public class SourceUnavailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SourceUnavailableCriterion(SerializerFactory serializerFactory) {
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
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // Must use exact criterion name (case-sensitive) as required
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job entity = context.entity();
         if (entity == null) {
             logger.warn("SourceUnavailableCriterion invoked with null entity");
             return EvaluationOutcome.fail("Entity is missing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Use only existing entity properties
         String source = entity.getSource();
         if (source == null || source.isBlank()) {
             // Missing source prevents ingestion; this is a validation error on the Job
             return EvaluationOutcome.fail("Job source is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String trimmed = source.trim();
         String lower = trimmed.toLowerCase();
         if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
             // Source value is present but not a valid endpoint format -> data quality issue
             return EvaluationOutcome.fail("Job source is not a valid URL (must start with http:// or https://)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Ensure the URL parses and has a host — lightweight precondition for availability
         try {
             URI uri = new URI(trimmed);
             String host = uri.getHost();
             if (host == null || host.isBlank()) {
                 logger.debug("Job source URI has no host: {}", trimmed);
                 return EvaluationOutcome.fail("Job source URL has no host component", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } catch (URISyntaxException e) {
             logger.debug("Job source URI parsing failed: {} -> {}", trimmed, e.getMessage());
             return EvaluationOutcome.fail("Job source is not a well-formed URI: " + e.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Precondition: job must be in SCHEDULED state before ingestion is attempted.
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Job status is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!"SCHEDULED".equals(status.trim())) {
             // If job isn't scheduled, it's a business-rule violation to attempt ingestion now
             return EvaluationOutcome.fail("Job is not in SCHEDULED state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If all lightweight checks pass we consider source as available from an entity-validation perspective.
         return EvaluationOutcome.success();
    }
}