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
         MonthlyReport entity = context.entity();

         // Basic presence checks
         if (entity.getMonth() == null || entity.getMonth().isBlank()) {
             return EvaluationOutcome.fail("Report month is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // month format YYYY-MM
         String runMonthPattern = "^\\d{4}-\\d{2}$";
         if (!entity.getMonth().matches(runMonthPattern)) {
             return EvaluationOutcome.fail("Report month must be in YYYY-MM format", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
             return EvaluationOutcome.fail("generatedAt timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Numeric metrics presence and non-negative checks
         if (entity.getTotalUsers() == null || entity.getTotalUsers() < 0) {
             return EvaluationOutcome.fail("totalUsers must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getNewUsers() == null || entity.getNewUsers() < 0) {
             return EvaluationOutcome.fail("newUsers must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getInvalidUsers() == null || entity.getInvalidUsers() < 0) {
             return EvaluationOutcome.fail("invalidUsers must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         // Consistency: totalUsers == newUsers + invalidUsers
         if (entity.getTotalUsers().intValue() != (entity.getNewUsers().intValue() + entity.getInvalidUsers().intValue())) {
             return EvaluationOutcome.fail("totalUsers must equal newUsers + invalidUsers", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rules for send criterion:
         // The criterion passes only if report has been published successfully.
         // If publishing failed, return business rule failure.
         // If still publishing or not yet published, return business rule failure.
         if ("PUBLISHED".equalsIgnoreCase(status)) {
             // Ensure delivery metadata and file reference present for published reports
             if (entity.getFileRef() == null || entity.getFileRef().isBlank()) {
                 return EvaluationOutcome.fail("fileRef is required for published reports", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (entity.getDeliveryAt() == null || entity.getDeliveryAt().isBlank()) {
                 return EvaluationOutcome.fail("deliveryAt timestamp is required for published reports", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         if ("FAILED".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("Report publishing failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // For READY, GENERATING, RENDERING, PUBLISHING and other intermediate states, do not allow transition
         return EvaluationOutcome.fail("Report is not published yet (current status: " + status + ")", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}