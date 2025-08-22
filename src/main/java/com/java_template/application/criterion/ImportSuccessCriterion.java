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

import java.util.Collection;
import java.util.Objects;

@Component
public class ImportSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ImportSuccessCriterion(SerializerFactory serializerFactory) {
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
            return EvaluationOutcome.fail("Missing PetIngestionJob entity", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        String status = job.getStatus();
        if (status == null || status.trim().isEmpty()) {
            return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Completed with no errors -> success
        if ("completed".equalsIgnoreCase(status.trim())) {
            // completedAt should be present for a completed job
            String completedAt = job.getCompletedAt();
            if (completedAt == null || completedAt.trim().isEmpty()) {
                return EvaluationOutcome.fail("completedAt is missing for a completed job", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // If errors were recorded despite completion, treat as data quality issue
            try {
                Collection<?> errors = null;
                Object rawErrors = job.getErrors();
                if (rawErrors instanceof Collection) {
                    errors = (Collection<?>) rawErrors;
                }
                if (errors != null && !errors.isEmpty()) {
                    return EvaluationOutcome.fail("Job marked completed but contains errors", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                }
            } catch (Exception e) {
                logger.debug("Unable to evaluate job.errors: {}", e.getMessage());
                // If we cannot inspect errors, don't fail; report validation failure
                return EvaluationOutcome.fail("Unable to inspect job errors", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // importedCount should be non-negative (allow zero imports as a valid but notable result)
            Integer importedCount = null;
            try {
                Object rawCount = job.getImportedCount();
                if (rawCount instanceof Integer) {
                    importedCount = (Integer) rawCount;
                } else if (rawCount instanceof Number) {
                    importedCount = ((Number) rawCount).intValue();
                }
            } catch (Exception e) {
                // ignore parsing issues; not critical for success determination
            }
            if (Objects.nonNull(importedCount) && importedCount < 0) {
                return EvaluationOutcome.fail("importedCount is negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            return EvaluationOutcome.success();
        }

        // Failed state -> business rule failure
        if ("failed".equalsIgnoreCase(status.trim())) {
            return EvaluationOutcome.fail("Ingestion job failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // For any other state (pending, running, etc.) the job is not yet complete
        return EvaluationOutcome.fail("Ingestion job not completed (status=" + status + ")", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}