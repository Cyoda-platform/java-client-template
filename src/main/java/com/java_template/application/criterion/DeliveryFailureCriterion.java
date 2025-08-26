package com.java_template.application.criterion;

import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;
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
public class DeliveryFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DeliveryFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(WeeklySendJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<WeeklySendJob> context) {
         WeeklySendJob entity = context.entity();

         // Basic presence checks (use only existing fields/getters)
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             logger.debug("WeeklySendJob missing status: id={}", entity.getId());
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // This criterion evaluates delivery failure conditions.
         // If job is not marked FAILED, nothing to do -> success.
         if (!"FAILED".equals(entity.getStatus())) {
             return EvaluationOutcome.success();
         }

         // For FAILED jobs enforce business rules / data quality checks:

         // 1) A failed delivery job should reference the CatFact that it attempted to send.
         if (entity.getCatfactRef() == null || entity.getCatfactRef().isBlank()) {
             logger.info("Failed WeeklySendJob without catfactRef: id={}", entity.getId());
             return EvaluationOutcome.fail("Failed job must reference a CatFact (catfactRef is missing)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // 2) targetCount must be present for failed jobs (we expect the attempted recipient count to be recorded)
         if (entity.getTargetCount() == null) {
             logger.info("Failed WeeklySendJob missing targetCount: id={}", entity.getId());
             return EvaluationOutcome.fail("targetCount is required for failed delivery jobs", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // 3) targetCount should be a positive number when a failure occurred (zero indicates suspicious data)
         if (entity.getTargetCount() <= 0) {
             logger.info("Failed WeeklySendJob has non-positive targetCount: id={}, targetCount={}", entity.getId(), entity.getTargetCount());
             return EvaluationOutcome.fail("Failed job has non-positive targetCount", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // 4) scheduledDate should be present for traceability of the failed run
         if (entity.getScheduledDate() == null || entity.getScheduledDate().isBlank()) {
             logger.info("Failed WeeklySendJob missing scheduledDate: id={}", entity.getId());
             return EvaluationOutcome.fail("scheduledDate is required for failed delivery jobs", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // All checks passed for a FAILED job -> return success (criterion does not force remediation here)
         return EvaluationOutcome.success();
    }
}