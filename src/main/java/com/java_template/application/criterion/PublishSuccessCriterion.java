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
public class PublishSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PublishSuccessCriterion(SerializerFactory serializerFactory) {
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
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // Use exact criterion name match as required
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<MonthlyReport> context) {
         MonthlyReport entity = context.entity();

         // Basic validations for required publishing fields
         if (entity.getPublishedStatus() == null || entity.getPublishedStatus().isBlank()) {
             return EvaluationOutcome.fail("publishedStatus is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getReportFileRef() == null || entity.getReportFileRef().isBlank()) {
             return EvaluationOutcome.fail("reportFileRef is required for published reports", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (entity.getAdminRecipients() == null || entity.getAdminRecipients().isEmpty()) {
             return EvaluationOutcome.fail("adminRecipients must contain at least one recipient", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         for (String r : entity.getAdminRecipients()) {
             if (r == null || r.isBlank()) {
                 return EvaluationOutcome.fail("adminRecipients contains an invalid recipient", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         if (!"PUBLISHED".equalsIgnoreCase(entity.getPublishedStatus())) {
             // If status explicitly indicates failure, treat as business rule failure; otherwise generic business rule failure
             return EvaluationOutcome.fail("Report is not marked as PUBLISHED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Delivery attempts should be present (even if zero) to allow tracking, treat missing as data-quality issue
         if (entity.getDeliveryAttempts() == null || entity.getDeliveryAttempts() < 0) {
             return EvaluationOutcome.fail("deliveryAttempts must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed -> mark as success
         return EvaluationOutcome.success();
    }
}