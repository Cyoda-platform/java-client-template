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
public class ReportSendCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReportSendCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(MonthlyReport.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<MonthlyReport> context) {
         MonthlyReport entity = context.entity();

         if (entity == null) {
             logger.warn("MonthlyReport entity is null in ReportSendCriterion");
             return EvaluationOutcome.fail("Report entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic presence checks
         if (entity.getMonth() == null || entity.getMonth().isBlank()) {
             return EvaluationOutcome.fail("Report month is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
             return EvaluationOutcome.fail("generatedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getFileRef() == null || entity.getFileRef().isBlank()) {
             return EvaluationOutcome.fail("fileRef is required for publishing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Numeric consistency check
         Integer total = entity.getTotalUsers();
         Integer newUsers = entity.getNewUsers();
         Integer invalidUsers = entity.getInvalidUsers();
         if (total == null || newUsers == null || invalidUsers == null) {
             return EvaluationOutcome.fail("Report metrics (total/new/invalid) must be present", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (total.intValue() != (newUsers.intValue() + invalidUsers.intValue())) {
             return EvaluationOutcome.fail("Inconsistent metrics: totalUsers != newUsers + invalidUsers", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Determine final outcome based on status after SendReportProcessor
         String status = entity.getStatus().trim().toUpperCase();
         switch (status) {
             case "PUBLISHED":
                 // Published must have deliveryAt timestamp
                 if (entity.getDeliveryAt() == null || entity.getDeliveryAt().isBlank()) {
                     return EvaluationOutcome.fail("Published report must have deliveryAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 return EvaluationOutcome.success();
             case "FAILED":
                 return EvaluationOutcome.fail("Report sending failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             default:
                 // Not in a final publishing state - criterion not satisfied
                 return EvaluationOutcome.fail("Report not in PUBLISHED or FAILED state", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
    }
}