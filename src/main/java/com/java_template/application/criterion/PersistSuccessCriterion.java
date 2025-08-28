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

import java.lang.reflect.Method;

@Component
public class PersistSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PersistSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Business logic lives in validateEntity method.
        return serializer.withRequest(request) // always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetIngestionJob> context) {
         PetIngestionJob entity = context.entity();

         if (entity == null) {
             logger.warn("PersistSuccessCriterion - entity payload is null");
             return EvaluationOutcome.fail("Entity payload is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Safe retrieval of status and processedCount using reflection to avoid compile-time dependency on getter names
         String status = safeGetStatus(entity);
         Integer processedCount = safeGetProcessedCount(entity);

         // Presence validation
         if (status == null || status.isBlank()) {
             logger.debug("PersistSuccessCriterion - job status missing for jobName='{}'", entity.getJobName());
             return EvaluationOutcome.fail("Job status is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: job must be completed to be considered a successful persist
         if (!"COMPLETED".equalsIgnoreCase(status)) {
             logger.debug("PersistSuccessCriterion - job not completed (status='{}') for jobName='{}'", status, entity.getJobName());
             return EvaluationOutcome.fail("Ingestion job is not completed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality: completedAt must be present for COMPLETED jobs
         if (entity.getCompletedAt() == null || entity.getCompletedAt().isBlank()) {
             logger.debug("PersistSuccessCriterion - completedAt missing for completed job '{}'", entity.getJobName());
             return EvaluationOutcome.fail("completedAt must be provided for a completed job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Data quality: processedCount must be present and positive (a completed persist should have processed items)
         if (processedCount == null) {
             logger.debug("PersistSuccessCriterion - processedCount missing for job '{}'", entity.getJobName());
             return EvaluationOutcome.fail("processedCount is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (processedCount <= 0) {
             logger.debug("PersistSuccessCriterion - processedCount not positive ({} ) for job '{}'", processedCount, entity.getJobName());
             return EvaluationOutcome.fail("processedCount must be greater than zero for a successful persist", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Data quality: there should be no errors recorded for a successful persist
         if (entity.getErrors() != null && !entity.getErrors().isEmpty()) {
             logger.debug("PersistSuccessCriterion - job '{}' completed but contains {} error(s)", entity.getJobName(), entity.getErrors().size());
             return EvaluationOutcome.fail("Errors present in job result", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed -> success
         logger.info("PersistSuccessCriterion - ingestion job '{}' marked as successful persist (processedCount={})", entity.getJobName(), processedCount);
         return EvaluationOutcome.success();
    }

    // Reflection helper to safely obtain status without requiring compile-time presence of getStatus()
    private String safeGetStatus(PetIngestionJob entity) {
        if (entity == null) return null;
        try {
            Method m = entity.getClass().getMethod("getStatus");
            Object val = m.invoke(entity);
            return val == null ? null : val.toString();
        } catch (NoSuchMethodException e) {
            logger.debug("PersistSuccessCriterion - getStatus() not found on entity: {}", e.toString());
            return null;
        } catch (Exception e) {
            logger.debug("PersistSuccessCriterion - error invoking getStatus(): {}", e.toString());
            return null;
        }
    }

    // Reflection helper to safely obtain processedCount without requiring compile-time presence of getProcessedCount()
    private Integer safeGetProcessedCount(PetIngestionJob entity) {
        if (entity == null) return null;
        try {
            Method m = entity.getClass().getMethod("getProcessedCount");
            Object val = m.invoke(entity);
            if (val == null) return null;
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
            try {
                return Integer.valueOf(val.toString());
            } catch (NumberFormatException nfe) {
                logger.debug("PersistSuccessCriterion - processedCount not a number: {}", nfe.toString());
                return null;
            }
        } catch (NoSuchMethodException e) {
            logger.debug("PersistSuccessCriterion - getProcessedCount() not found on entity: {}", e.toString());
            return null;
        } catch (Exception e) {
            logger.debug("PersistSuccessCriterion - error invoking getProcessedCount(): {}", e.toString());
            return null;
        }
    }
}