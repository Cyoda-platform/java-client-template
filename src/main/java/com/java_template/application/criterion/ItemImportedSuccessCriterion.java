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

@Component
public class ItemImportedSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ItemImportedSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(ImportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ImportJob> context) {
         ImportJob entity = context.entity();
         if (entity == null) {
             logger.debug("ImportJob entity is null in context");
             return EvaluationOutcome.fail("ImportJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("ImportJob.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Success condition: job marked COMPLETED and processedItemId present and positive
         if ("COMPLETED".equalsIgnoreCase(status)) {
             Long processedId = entity.getProcessedItemId();
             if (processedId == null) {
                 return EvaluationOutcome.fail("processedItemId missing for completed job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (processedId <= 0) {
                 return EvaluationOutcome.fail("processedItemId must be a positive number", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // Explicit failure state reported by workflow
         if ("FAILED".equalsIgnoreCase(status)) {
             String jobRef = entity.getJobId();
             String msg = "ImportJob marked FAILED" + (jobRef != null && !jobRef.isBlank() ? " (jobId=" + jobRef + ")" : "");
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Any other statuses (e.g., PENDING, PROCESSING, WAIT_FOR_ITEM) are not success yet
         return EvaluationOutcome.fail("ImportJob not completed (status=" + status + ")", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}