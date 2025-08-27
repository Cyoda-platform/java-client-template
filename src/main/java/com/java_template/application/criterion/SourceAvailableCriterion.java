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

@Component
public class SourceAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SourceAvailableCriterion(SerializerFactory serializerFactory) {
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
        // must match the exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();

         if (job == null) {
             logger.warn("Job entity is null in SourceAvailableCriterion");
             return EvaluationOutcome.fail("Job entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String source = job.getSource();
         if (source == null || source.isBlank()) {
             logger.debug("Job {} missing source", job.getId());
             return EvaluationOutcome.fail("source is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: this criterion applies when job is in SCHEDULED state
         String status = job.getStatus();
         if (status == null || !status.equalsIgnoreCase("SCHEDULED")) {
             logger.debug("Job {} in state '{}' - SourceAvailableCriterion expected SCHEDULED", job.getId(), status);
             return EvaluationOutcome.fail("job must be in SCHEDULED state to validate source availability", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Basic data quality check: ensure source is a well-formed http(s) URI
         try {
             URI uri = new URI(source);
             String scheme = uri.getScheme();
             if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                 logger.debug("Job {} has unsupported source scheme: {}", job.getId(), scheme);
                 return EvaluationOutcome.fail("source must be a valid http/https URL", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (uri.getHost() == null || uri.getHost().isBlank()) {
                 logger.debug("Job {} source has no host: {}", job.getId(), source);
                 return EvaluationOutcome.fail("source must contain a valid host", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } catch (Exception e) {
             logger.debug("Job {} source URI parsing failed: {}", job.getId(), e.getMessage());
             return EvaluationOutcome.fail("source is not a valid URL", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed - we consider the source available at metadata/format level.
         return EvaluationOutcome.success();
    }
}