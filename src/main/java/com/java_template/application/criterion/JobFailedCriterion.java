package com.java_template.application.criterion;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
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
public class JobFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(ReportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ReportJob> context) {
         ReportJob entity = context.entity();
         if (entity == null) {
             logger.debug("ReportJob entity is null in JobFailedCriterion");
             return EvaluationOutcome.fail("ReportJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Status must be present
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("ReportJob.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If the job explicitly reports FAILED, mark as business-rule failure
         if ("FAILED".equalsIgnoreCase(status)) {
             StringBuilder reason = new StringBuilder("ReportJob status indicates FAILURE");
             // add contextual hints about likely causes using only existing fields
             if (entity.getTitle() == null || entity.getTitle().isBlank()) {
                 reason.append(": missing title");
             }
             if (entity.getRequestedBy() == null || entity.getRequestedBy().isBlank()) {
                 reason.append(reason.length() > 0 ? ", missing requestedBy" : "missing requestedBy");
             }
             if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
                 reason.append(reason.length() > 0 ? ", missing createdAt" : "missing createdAt");
             }
             if (entity.getExportFormats() == null || entity.getExportFormats().isEmpty()) {
                 reason.append(reason.length() > 0 ? ", missing exportFormats" : "missing exportFormats");
             }
             return EvaluationOutcome.fail(reason.toString(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // For non-failed jobs, ensure required fields exist (validation)
         if (entity.getTitle() == null || entity.getTitle().isBlank()) {
             return EvaluationOutcome.fail("ReportJob.title is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRequestedBy() == null || entity.getRequestedBy().isBlank()) {
             return EvaluationOutcome.fail("ReportJob.requestedBy is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("ReportJob.createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getExportFormats() == null || entity.getExportFormats().isEmpty()) {
             return EvaluationOutcome.fail("ReportJob.exportFormats must contain at least one format", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If all checks pass, return success
         return EvaluationOutcome.success();
    }
}