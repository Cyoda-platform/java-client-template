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
public class PublishFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PublishFailureCriterion(SerializerFactory serializerFactory) {
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
        // MUST use exact criterion name (case-sensitive)
        return modelSpec != null && className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<MonthlyReport> context) {
         MonthlyReport entity = context.entity();
         if (entity == null) {
             logger.warn("MonthlyReport entity is null in PublishFailureCriterion");
             return EvaluationOutcome.fail("MonthlyReport entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String publishedStatus = entity.getPublishedStatus();
         if (publishedStatus == null || publishedStatus.isBlank()) {
             // publishedStatus is required to determine publish outcome
             logger.warn("MonthlyReport[month={}] has missing publishedStatus", entity.getMonth());
             return EvaluationOutcome.fail("publishedStatus is required to evaluate publishing outcome", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If publishing explicitly failed, mark as a business rule failure so the workflow can transition to FAILED
         if ("FAILED".equalsIgnoreCase(publishedStatus)) {
             Integer attempts = entity.getDeliveryAttempts();
             String attemptsInfo = (attempts == null) ? "unknown" : String.valueOf(attempts);

             // If admin recipients are invalid or missing, surface data quality issue in addition to failure
             if (entity.getAdminRecipients() == null || entity.getAdminRecipients().isEmpty()) {
                 logger.warn("MonthlyReport[month={}] publishing failed but adminRecipients are missing", entity.getMonth());
                 return EvaluationOutcome.fail("Publish failed and admin recipients missing (attempts: " + attemptsInfo + ")", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             logger.info("MonthlyReport[month={}] publishing failed after {} attempts", entity.getMonth(), attemptsInfo);
             return EvaluationOutcome.fail("Report publishing failed (attempts: " + attemptsInfo + ")", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Not a failure state (e.g., PENDING_PUBLISH, PUBLISHING, PUBLISHED) => success for this failure-check criterion
         return EvaluationOutcome.success();
    }
}