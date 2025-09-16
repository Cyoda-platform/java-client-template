package com.java_template.application.processor;

import com.java_template.application.entity.email_report.version_1.EmailReport;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * EmailReportSendProcessor - Send email via email service
 * 
 * Transition: prepared → sending OR retry → sending
 * Purpose: Send email via email service
 */
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
        logger.info("Processing EmailReport sending for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailReport.class)
                .validate(this::isValidEntityWithMetadata, "Invalid email report entity")
                .map(this::processEmailSending)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EmailReport> entityWithMetadata) {
        EmailReport entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for email sending
     */
    private EntityWithMetadata<EmailReport> processEmailSending(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailReport> context) {

        EntityWithMetadata<EmailReport> entityWithMetadata = context.entityResponse();
        EmailReport report = entityWithMetadata.entity();

        logger.debug("Sending email report: {}", report.getReportId());

        // Set status to sending
        report.setDeliveryStatus("SENDING");

        try {
            // Simulate email sending (in real implementation, this would call external email service)
            sendEmailViaExternalService(report);
            
            // If we reach here, email was sent successfully
            // The workflow will automatically transition to "sent" state via email_delivered transition
            logger.info("Email sending initiated successfully for report: {}", report.getReportId());
            
        } catch (Exception e) {
            // If email sending fails, set error message and status
            report.setErrorMessage(e.getMessage());
            report.setDeliveryStatus("FAILED");
            logger.error("Email sending failed for report: {}", report.getReportId(), e);
            
            // The workflow will automatically transition to "failed" state via email_failed transition
        }

        logger.info("Email sending attempted for report: {}", report.getReportId());

        return entityWithMetadata;
    }

    /**
     * Simulate external email service call
     * In a real implementation, this would integrate with an actual email service
     */
    private void sendEmailViaExternalService(EmailReport report) {
        logger.info("Simulating email send to: {} with subject: {}", 
                   report.getRecipientEmail(), report.getSubject());
        
        // Simulate potential failure scenarios for testing
        // In real implementation, this would be actual email service integration
        
        // For demonstration, we'll simulate success most of the time
        // but occasionally fail to test retry logic
        double random = Math.random();
        if (random < 0.1) { // 10% chance of failure for testing
            throw new RuntimeException("Simulated email service failure");
        }
        
        // Simulate email service processing time
        try {
            Thread.sleep(100); // 100ms delay to simulate network call
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Email sending interrupted", e);
        }
        
        logger.info("Email sent successfully via external service");
    }
}
