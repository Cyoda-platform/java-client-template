package com.java_template.application.criterion;

import com.java_template.application.entity.emailreport.version_1.EmailReport;
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

import java.util.regex.Pattern;

@Component
public class EmailReportContentReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    public EmailReportContentReadyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(EmailReport.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<EmailReport> context) {
        EmailReport entity = context.entity();
        
        try {
            // Check if subject is not null and not empty
            if (entity.getSubject() == null || entity.getSubject().trim().isEmpty()) {
                return EvaluationOutcome.fail("Email subject cannot be null or empty", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if htmlContent is not null and not empty
            if (entity.getHtmlContent() == null || entity.getHtmlContent().trim().isEmpty()) {
                return EvaluationOutcome.fail("Email HTML content cannot be null or empty", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if textContent is not null and not empty
            if (entity.getTextContent() == null || entity.getTextContent().trim().isEmpty()) {
                return EvaluationOutcome.fail("Email text content cannot be null or empty", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if recipientEmail is valid email format
            if (entity.getRecipientEmail() == null || !isValidEmail(entity.getRecipientEmail())) {
                return EvaluationOutcome.fail("Recipient email is not in valid format", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Validate HTML content contains expected sections
            String htmlContent = entity.getHtmlContent().toLowerCase();
            if (!htmlContent.contains("analysis summary")) {
                return EvaluationOutcome.fail("HTML content missing analysis summary section", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (!htmlContent.contains("metrics")) {
                return EvaluationOutcome.fail("HTML content missing metrics section", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (!htmlContent.contains("keywords")) {
                return EvaluationOutcome.fail("HTML content missing keywords section", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (!htmlContent.contains("sentiment")) {
                return EvaluationOutcome.fail("HTML content missing sentiment section", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Validate text content is properly formatted
            String textContent = entity.getTextContent().toLowerCase();
            if (!textContent.contains("comment analysis report")) {
                return EvaluationOutcome.fail("Text content missing report header", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            if (!textContent.contains("metrics:")) {
                return EvaluationOutcome.fail("Text content missing metrics section", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            logger.info("Email content is ready for EmailReport with reportId: {}", entity.getReportId());
            return EvaluationOutcome.success();
            
        } catch (Exception e) {
            logger.error("Error validating email content for reportId: {}", entity.getReportId(), e);
            return EvaluationOutcome.fail("Error validating email content: " + e.getMessage(), 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
}
