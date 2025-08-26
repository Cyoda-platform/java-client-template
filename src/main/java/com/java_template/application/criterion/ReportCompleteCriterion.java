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
public class ReportCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReportCompleteCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<MonthlyReport> context) {
         MonthlyReport report = context.entity();
         if (report == null) {
             logger.warn("MonthlyReport entity is null");
             return EvaluationOutcome.fail("MonthlyReport entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate required string fields
         if (report.getMonth() == null || report.getMonth().isBlank()) {
             return EvaluationOutcome.fail("month is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String monthPattern = "^\\d{4}-\\d{2}$";
         if (!report.getMonth().matches(monthPattern)) {
             return EvaluationOutcome.fail("month must be in YYYY-MM format", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (report.getGeneratedAt() == null || report.getGeneratedAt().isBlank()) {
             return EvaluationOutcome.fail("generatedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (report.getFileRef() == null || report.getFileRef().isBlank()) {
             return EvaluationOutcome.fail("fileRef is required for a completed report", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (report.getStatus() == null || report.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: report must be in READY state to be considered complete
         if (!"READY".equalsIgnoreCase(report.getStatus())) {
             return EvaluationOutcome.fail("report status must be READY to proceed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Validate numeric fields
         if (report.getTotalUsers() == null || report.getTotalUsers() < 0) {
             return EvaluationOutcome.fail("totalUsers must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (report.getNewUsers() == null || report.getNewUsers() < 0) {
             return EvaluationOutcome.fail("newUsers must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (report.getInvalidUsers() == null || report.getInvalidUsers() < 0) {
             return EvaluationOutcome.fail("invalidUsers must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Consistency check: totals must add up
         if (report.getTotalUsers().intValue() != (report.getNewUsers().intValue() + report.getInvalidUsers().intValue())) {
             return EvaluationOutcome.fail("totalUsers must equal newUsers + invalidUsers", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}