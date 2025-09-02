package com.java_template.application.processor;

import com.java_template.application.entity.emailreport.version_1.EmailReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class EmailReportConfirmProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailReportConfirmProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailReportConfirmProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailReport confirm for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailReport.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(EmailReport entity) {
        return entity != null && entity.isValid();
    }

    private EmailReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<EmailReport> context) {
        EmailReport entity = context.entity();

        try {
            // Check email service delivery status
            // In a real implementation, this would check with the actual email service
            boolean deliveryConfirmed = checkEmailDeliveryStatus(entity);
            
            if (deliveryConfirmed) {
                // Set emailStatus to "SENT"
                entity.setEmailStatus("SENT");
                
                // Set sentAt timestamp
                entity.setSentAt(LocalDateTime.now());
                
                // Log successful email delivery
                logger.info("Email successfully delivered for EmailReport with reportId: {} to {} at {}", 
                           entity.getReportId(), entity.getRecipientEmail(), entity.getSentAt());
            } else {
                // If delivery failed, set appropriate status
                entity.setEmailStatus("FAILED");
                entity.setFailedAt(LocalDateTime.now());
                entity.setFailureReason("Email delivery confirmation failed");
                
                logger.error("Email delivery failed for EmailReport with reportId: {}", entity.getReportId());
                throw new RuntimeException("Email delivery confirmation failed");
            }

        } catch (Exception e) {
            logger.error("Failed to confirm email delivery for reportId: {}", entity.getReportId(), e);
            entity.setEmailStatus("FAILED");
            entity.setFailedAt(LocalDateTime.now());
            entity.setFailureReason("Email confirmation failed: " + e.getMessage());
            throw new RuntimeException("Failed to confirm email delivery: " + e.getMessage(), e);
        }

        return entity;
    }

    private boolean checkEmailDeliveryStatus(EmailReport emailReport) {
        // This is a simulation of checking email delivery status
        // In a real implementation, you would check with the email service provider
        // for delivery confirmation, bounce notifications, etc.
        
        logger.info("Checking email delivery status for reportId: {}", emailReport.getReportId());
        
        // Validate that sending was started
        if (emailReport.getSendingStartedAt() == null) {
            logger.warn("Email sending was never started for reportId: {}", emailReport.getReportId());
            return false;
        }
        
        // Validate that the email status is SENDING
        if (!"SENDING".equals(emailReport.getEmailStatus())) {
            logger.warn("Email status is not SENDING for reportId: {}, current status: {}", 
                       emailReport.getReportId(), emailReport.getEmailStatus());
            return false;
        }
        
        // Simulate checking with email service
        // In real implementation, you would make API calls to check delivery status
        try {
            Thread.sleep(50); // Simulate API call delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        
        // For simulation purposes, we'll assume delivery is always successful
        // In real implementation, you would parse the actual response from the email service
        return true;
    }
}
