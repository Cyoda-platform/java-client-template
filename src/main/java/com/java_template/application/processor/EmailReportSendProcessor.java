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
public class EmailReportSendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailReportSendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EmailReportSendProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailReport send for request: {}", request.getId());

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
            // Simulate email service sending
            // In a real implementation, this would integrate with an actual email service
            logger.info("Sending email to: {} with subject: {}", entity.getRecipientEmail(), entity.getSubject());
            
            // Set emailStatus to "SENDING"
            entity.setEmailStatus("SENDING");
            
            // Set sendingStartedAt timestamp
            entity.setSendingStartedAt(LocalDateTime.now());
            
            // Simulate email sending process
            // In real implementation, this would call an email service API
            simulateEmailSending(entity);
            
            logger.info("Email sending initiated for EmailReport with reportId: {} to {}", 
                       entity.getReportId(), entity.getRecipientEmail());

        } catch (Exception e) {
            logger.error("Failed to send email for reportId: {}", entity.getReportId(), e);
            entity.setEmailStatus("FAILED");
            entity.setFailedAt(LocalDateTime.now());
            entity.setFailureReason("Email sending failed: " + e.getMessage());
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }

        return entity;
    }

    private void simulateEmailSending(EmailReport emailReport) {
        // This is a simulation of email sending
        // In a real implementation, you would integrate with services like:
        // - SendGrid
        // - Amazon SES
        // - JavaMail API
        // - Spring Boot Mail
        
        logger.info("Simulating email send to: {}", emailReport.getRecipientEmail());
        logger.info("Email subject: {}", emailReport.getSubject());
        logger.info("Email HTML content length: {}", 
                   emailReport.getHtmlContent() != null ? emailReport.getHtmlContent().length() : 0);
        logger.info("Email text content length: {}", 
                   emailReport.getTextContent() != null ? emailReport.getTextContent().length() : 0);
        
        // Simulate some processing time
        try {
            Thread.sleep(100); // Simulate network delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Email sending interrupted", e);
        }
        
        // For simulation purposes, we'll assume the email is always sent successfully
        // In real implementation, you would check the actual response from the email service
    }
}
