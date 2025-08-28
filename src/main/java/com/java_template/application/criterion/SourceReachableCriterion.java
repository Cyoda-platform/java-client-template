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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetIngestionJob> context) {
         PetIngestionJob job = context.entity();
         if (job == null) {
             logger.warn("PetIngestionJob entity is null in SourceReachableCriterion");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String sourceUrl = job.getSourceUrl();
         if (sourceUrl == null || sourceUrl.isBlank()) {
             logger.debug("PetIngestionJob {} has no sourceUrl", job.getJobName());
             return EvaluationOutcome.fail("sourceUrl is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String lower = sourceUrl.toLowerCase();
         if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
             logger.debug("PetIngestionJob {} has invalid sourceUrl: {}", job.getJobName(), sourceUrl);
             return EvaluationOutcome.fail("sourceUrl must be a valid http/https URL", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic defensive checks: if job already marked FAILED, surface as business rule failure
         String status = job.getStatus();
         if (status != null && status.equalsIgnoreCase("FAILED")) {
             logger.debug("PetIngestionJob {} is already FAILED", job.getJobName());
             return EvaluationOutcome.fail("Job is marked as FAILED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // No definitive network check here; the criterion ensures configuration looks valid for reachability.
         return EvaluationOutcome.success();
    }
}