package com.java_template.application.criterion;

import com.java_template.application.entity.analysisreport.version_1.AnalysisReport;
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
public class EmailSentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailSentCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(AnalysisReport.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AnalysisReport> context) {
         AnalysisReport entity = context.entity();

         if (entity == null) {
             logger.warn("AnalysisReport entity is null in EmailSentCriterion");
             return EvaluationOutcome.fail("AnalysisReport entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate essential identifiers
         if (entity.getReportId() == null || entity.getReportId().isBlank()) {
             return EvaluationOutcome.fail("reportId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate recipient email presence
         String recipient = entity.getRecipientEmail();
         if (recipient == null || recipient.isBlank()) {
             return EvaluationOutcome.fail("recipientEmail is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Basic email format sanity check (do not over-validate here)
         if (!recipient.contains("@") || !recipient.contains(".")) {
             return EvaluationOutcome.fail("recipientEmail appears invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Status must indicate SENT for this criterion to pass
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!"SENT".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("Report not in SENT state (current: " + status + ")", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // When status is SENT, sentAt should be present
         String sentAt = entity.getSentAt();
         if (sentAt == null || sentAt.isBlank()) {
             return EvaluationOutcome.fail("sentAt missing for report with status SENT", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}