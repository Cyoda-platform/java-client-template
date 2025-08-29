package com.java_template.application.criterion;

import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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
public class ReportGenerationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReportGenerationFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(WeeklyReport.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<WeeklyReport> context) {
         WeeklyReport entity = context.entity();

         // Basic presence check for required fields used in evaluation
         if (entity == null) {
             logger.debug("WeeklyReport entity is null in context");
             return EvaluationOutcome.fail("WeeklyReport entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Report status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Explicit failure case: report generation recorded as FAILED
         if ("FAILED".equalsIgnoreCase(status)) {
             String id = entity.getReportId();
             String msg = id != null && !id.isBlank()
                     ? String.format("Report generation failed for reportId=%s", id)
                     : "Report generation failed";
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality checks for completed/ready/dispatch states
         if ("READY".equalsIgnoreCase(status) || "DISPATCHED".equalsIgnoreCase(status)) {
             // generatedAt must be present for completed reports
             if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
                 return EvaluationOutcome.fail("generatedAt is missing for a completed report", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // attachmentUrl should normally be present when report is READY or DISPATCHED
             if (entity.getAttachmentUrl() == null || entity.getAttachmentUrl().isBlank()) {
                 return EvaluationOutcome.fail("attachmentUrl is missing for a completed report", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // If report is still generating, consider it a success for this criterion (no failure)
         return EvaluationOutcome.success();
    }
}