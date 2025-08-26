package com.java_template.application.criterion;

import com.java_template.application.entity.monthlyreport.version_1.MonthlyReport;
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
public class ReportSendFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReportSendFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(MonthlyReport.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<MonthlyReport> context) {
         MonthlyReport report = context.entity();
         if (report == null) {
             logger.debug("MonthlyReport entity is null in {}", className);
             return EvaluationOutcome.fail("MonthlyReport entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String month = report.getMonth();
         String generatedAt = report.getGeneratedAt();
         String fileRef = report.getFileRef();
         String status = report.getStatus();
         String deliveryAt = report.getDeliveryAt();
         Integer totalUsers = report.getTotalUsers();
         Integer newUsers = report.getNewUsers();
         Integer invalidUsers = report.getInvalidUsers();

         // Basic required fields validation (relevant for send step)
         if (month == null || month.isBlank()) {
             return EvaluationOutcome.fail("Report month is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (generatedAt == null || generatedAt.isBlank()) {
             return EvaluationOutcome.fail("generatedAt timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality check for metrics consistency if metrics present
         if (totalUsers != null || newUsers != null || invalidUsers != null) {
             if (totalUsers == null || newUsers == null || invalidUsers == null) {
                 return EvaluationOutcome.fail("Incomplete user metrics: totalUsers, newUsers and invalidUsers must all be present", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (totalUsers < 0 || newUsers < 0 || invalidUsers < 0) {
                 return EvaluationOutcome.fail("User metrics must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (!totalUsers.equals(newUsers + invalidUsers)) {
                 return EvaluationOutcome.fail("Inconsistent metrics: totalUsers != newUsers + invalidUsers", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rules around sending/publishing
         // If sending failed, it must be surfaced as a business rule failure.
         if ("FAILED".equalsIgnoreCase(status)) {
             String reason = "Report sending failed for month " + month;
             if (fileRef != null && !fileRef.isBlank()) {
                 reason += " (fileRef present: " + fileRef + ")";
             }
             logger.info("{} - {}", className, reason);
             return EvaluationOutcome.fail("Report sending failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If published, deliveryAt should be present
         if ("PUBLISHED".equalsIgnoreCase(status)) {
             if (deliveryAt == null || deliveryAt.isBlank()) {
                 return EvaluationOutcome.fail("Published report missing deliveryAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // published and deliveryAt present -> success
             return EvaluationOutcome.success();
         }

         // If status indicates ready/publishing but fileRef missing -> data quality issue
         if ("READY".equalsIgnoreCase(status) || "PUBLISHING".equalsIgnoreCase(status)) {
             if (fileRef == null || fileRef.isBlank()) {
                 return EvaluationOutcome.fail("Report has no fileRef while in status " + status, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // not yet published nor failed -> treat as success for this criterion (no failure)
             return EvaluationOutcome.success();
         }

         // Default: not a failure for send step
         return EvaluationOutcome.success();
    }
}