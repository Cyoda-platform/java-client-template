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

import java.time.LocalDateTime;

@Component
public class EmailReportSentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailReportSentCriterion(SerializerFactory serializerFactory) {
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
            // Check if emailStatus equals "SENT"
            if (!"SENT".equals(entity.getEmailStatus())) {
                return EvaluationOutcome.fail("Email status is not SENT, current status: " + entity.getEmailStatus(), 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if sentAt timestamp is set and not in the future
            if (entity.getSentAt() == null) {
                return EvaluationOutcome.fail("sentAt timestamp is not set", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            LocalDateTime now = LocalDateTime.now();
            if (entity.getSentAt().isAfter(now)) {
                return EvaluationOutcome.fail("sentAt timestamp cannot be in the future", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Check if sendingStartedAt timestamp exists
            if (entity.getSendingStartedAt() == null) {
                return EvaluationOutcome.fail("sendingStartedAt timestamp is not set", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Validate that sentAt is after sendingStartedAt
            if (entity.getSentAt().isBefore(entity.getSendingStartedAt())) {
                return EvaluationOutcome.fail("sentAt timestamp cannot be before sendingStartedAt", 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Verify email service delivery confirmation (if available)
            // In a real implementation, this would check with the email service provider
            // For now, we'll just validate the basic fields are consistent

            logger.info("Email has been successfully sent for EmailReport with reportId: {}", entity.getReportId());
            return EvaluationOutcome.success();
            
        } catch (Exception e) {
            logger.error("Error validating email sent status for reportId: {}", entity.getReportId(), e);
            return EvaluationOutcome.fail("Error validating email sent status: " + e.getMessage(), 
                                        StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}
