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
public class EmailFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailFailureCriterion(SerializerFactory serializerFactory) {
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
        // Use exact criterion name (case-sensitive) as required by critical rules
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ReportJob> context) {
         ReportJob entity = context.entity();

         // Ensure there are recipients
         if (entity.getRecipients() == null || entity.getRecipients().isBlank()) {
             return EvaluationOutcome.fail("Recipients are missing for report email", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate recipient email formats (basic validation per-email)
         String[] recipients = entity.getRecipients().split(",");
         for (String raw : recipients) {
             String email = raw.trim();
             if (email.isEmpty()) {
                 return EvaluationOutcome.fail("One or more recipient entries are empty", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             int atIndex = email.indexOf('@');
             if (atIndex <= 0 || atIndex != email.lastIndexOf('@') || atIndex == email.length() - 1) {
                 return EvaluationOutcome.fail("Invalid recipient email address: " + email, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             String domainPart = email.substring(atIndex + 1);
             if (!domainPart.contains(".") || domainPart.startsWith(".") || domainPart.endsWith(".")) {
                 return EvaluationOutcome.fail("Invalid recipient email address: " + email, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // For sending emails with attachments the generatedUrl must be present
         if (entity.getGeneratedUrl() == null || entity.getGeneratedUrl().isBlank()) {
             return EvaluationOutcome.fail("Generated report URL is missing; cannot attach report to email", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Output formats should be defined (to determine attachments expected)
         if (entity.getOutputFormats() == null || entity.getOutputFormats().isBlank()) {
             return EvaluationOutcome.fail("Output formats not specified for report", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}