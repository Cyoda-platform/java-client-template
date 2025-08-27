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
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(ReportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // Must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ReportJob> context) {
         ReportJob entity = context.entity();
         if (entity == null) {
             logger.warn("EmailSentCriterion: entity is null in evaluation context");
             return EvaluationOutcome.fail("ReportJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // status is required for evaluation
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("ReportJob.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus().trim();

         // If the report job completed, ensure email artifacts exist and recipients are valid
         if ("COMPLETED".equalsIgnoreCase(status)) {
             // generatedUrl must be present for completed jobs
             if (entity.getGeneratedUrl() == null || entity.getGeneratedUrl().isBlank()) {
                 return EvaluationOutcome.fail("generatedUrl is missing for completed ReportJob", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             // recipients must be present and contain at least one valid email
             if (entity.getRecipients() == null || entity.getRecipients().isBlank()) {
                 return EvaluationOutcome.fail("recipients are missing for completed ReportJob", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             String[] parts = entity.getRecipients().split(",");
             boolean hasValid = false;
             for (String p : parts) {
                 String email = p == null ? "" : p.trim();
                 if (!email.isEmpty() && isValidEmail(email)) {
                     hasValid = true;
                     break;
                 }
             }
             if (!hasValid) {
                 return EvaluationOutcome.fail("no valid recipient email addresses found", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             // All checks passed
             return EvaluationOutcome.success();
         }

         // If sending failed -> business rule failure
         if ("FAILED".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("email sending failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If still sending, consider as not yet satisfied (business in-progress)
         if ("SENDING".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("email is still in sending state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // For other statuses, this criterion is not satisfied
         return EvaluationOutcome.fail("ReportJob status is not in a state that indicates email was sent", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }

    private boolean isValidEmail(String email) {
        // Simple validation: contains one '@' and at least one '.' after '@'
        if (email == null) return false;
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) return false;
        String domain = email.substring(at + 1);
        return domain.contains(".");
    }
}