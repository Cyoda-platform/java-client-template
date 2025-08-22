package com.java_template.application.criterion;

import com.java_template.application.entity.petsyncjob.version_1.PetSyncJob;
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
public class ParseFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ParseFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetSyncJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetSyncJob> context) {
         PetSyncJob job = context.entity();

         if (job == null) {
             logger.warn("ParseFailureCriterion: received null PetSyncJob entity");
             return EvaluationOutcome.fail("PetSyncJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Use entity's built-in validity check
         try {
             if (!job.isValid()) {
                 logger.warn("ParseFailureCriterion: PetSyncJob is not valid (id={})", job.getId());
                 return EvaluationOutcome.fail("PetSyncJob missing required fields", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (Exception e) {
             // If isValid throws for any reason, treat as validation failure
             logger.warn("ParseFailureCriterion: exception while validating PetSyncJob (id={}), error={}", job.getId(), e.getMessage());
             return EvaluationOutcome.fail("PetSyncJob validation error", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = job.getStatus();
         String errorMessage = job.getErrorMessage();

         // Data quality check: fetchedCount must not be negative when present
         if (job.getFetchedCount() != null && job.getFetchedCount() < 0) {
             logger.warn("ParseFailureCriterion: fetchedCount negative for job id={}", job.getId());
             return EvaluationOutcome.fail("fetchedCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Detect parse failure conditions:
         // - Explicit failure status with an error message
         // - Parsing status but an errorMessage present (indicates parsing encountered errors)
         if (status != null) {
             String normalized = status.trim().toLowerCase();
             if ("failed".equals(normalized)) {
                 String msg = (errorMessage != null && !errorMessage.isBlank())
                     ? "Parsing failed: " + errorMessage
                     : "Job marked as failed during parsing";
                 logger.info("ParseFailureCriterion: job id={} marked failed: {}", job.getId(), errorMessage);
                 return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if ("parsing".equals(normalized) && errorMessage != null && !errorMessage.isBlank()) {
                 logger.info("ParseFailureCriterion: job id={} parsing produced errors: {}", job.getId(), errorMessage);
                 return EvaluationOutcome.fail("Parsing errors: " + errorMessage, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // No parse failure detected
         return EvaluationOutcome.success();
    }
}