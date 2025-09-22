package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EmailSendProcessor
 * 
 * Sends report via email to all active subscribers.
 * Used in Report workflow transitions: send_to_subscribers, retry_send
 */
@Component
public class EmailSendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailSendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailSendProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report email sending for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Report.class)
                .validate(this::isValidEntityWithMetadata, "Invalid Report entity")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Report> entityWithMetadata) {
        Report entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for email sending
     */
    private EntityWithMetadata<Report> processEmailSending(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Report> context) {

        EntityWithMetadata<Report> entityWithMetadata = context.entityResponse();
        Report report = entityWithMetadata.entity();

        logger.info("Sending emails for Report: {}", report.getReportId());

        try {
            // Find all active subscribers
            List<EntityWithMetadata<Subscriber>> activeSubscribers = findActiveSubscribers();
            
            // Load report content (in real implementation, this would load from storage)
            String reportContent = loadReportContent(report);
            
            int sentCount = 0;
            
            // Send email to each active subscriber
            for (EntityWithMetadata<Subscriber> subscriberWithMetadata : activeSubscribers) {
                Subscriber subscriber = subscriberWithMetadata.entity();
                
                try {
                    // Send email (in real implementation, this would use an email service)
                    sendEmail(subscriber, report, reportContent);
                    
                    // Update subscriber's last email sent timestamp
                    subscriber.setLastEmailSent(LocalDateTime.now());
                    entityService.update(subscriberWithMetadata.metadata().getId(), subscriber, null);
                    
                    sentCount++;
                    logger.debug("Email sent successfully to: {}", subscriber.getEmail());
                    
                } catch (Exception e) {
                    logger.warn("Failed to send email to subscriber: {}", subscriber.getEmail(), e);
                    // Continue with other subscribers even if one fails
                }
            }
            
            // Update report with recipient count
            report.setRecipientCount(sentCount);
            
            logger.info("Email sending completed for Report: {}, sent to {} recipients", 
                       report.getReportId(), sentCount);

        } catch (Exception e) {
            logger.error("Failed to send emails for Report: {}", report.getReportId(), e);
            throw new RuntimeException("Failed to send emails: " + e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Find all active subscribers
     */
    private List<EntityWithMetadata<Subscriber>> findActiveSubscribers() {
        ModelSpec modelSpec = new ModelSpec()
                .withName(Subscriber.ENTITY_NAME)
                .withVersion(Subscriber.ENTITY_VERSION);

        // Create search condition for active subscribers (state = "active")
        // Note: We're searching by entity state, not the isActive field
        SimpleCondition stateCondition = new SimpleCondition()
                .withJsonPath("$.state")
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree("active"));

        GroupCondition condition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(stateCondition));

        return entityService.search(modelSpec, condition, Subscriber.class);
    }

    /**
     * Load report content (placeholder implementation)
     */
    private String loadReportContent(Report report) {
        // In real implementation, this would load the formatted content from storage
        // For now, we'll generate a simple email content
        StringBuilder content = new StringBuilder();
        content.append("Subject: ").append(report.getTitle()).append("\n\n");
        content.append("Dear Subscriber,\n\n");
        content.append("Please find attached the latest analysis report.\n\n");
        
        if (report.getSummary() != null) {
            content.append("Summary:\n").append(report.getSummary()).append("\n\n");
        }
        
        content.append("Report generated on: ").append(report.getGeneratedAt()).append("\n");
        content.append("Report ID: ").append(report.getReportId()).append("\n\n");
        content.append("Best regards,\nData Analysis Team");
        
        return content.toString();
    }

    /**
     * Send email to subscriber (placeholder implementation)
     */
    private void sendEmail(Subscriber subscriber, Report report, String content) {
        // In real implementation, this would use an email service like SendGrid, AWS SES, etc.
        logger.info("Sending email to: {} with subject: {}", subscriber.getEmail(), report.getTitle());
        
        // Simulate email sending delay
        try {
            Thread.sleep(100); // 100ms delay to simulate email sending
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Log email details for debugging
        logger.debug("Email content preview for {}: {}", 
                    subscriber.getEmail(), 
                    content.length() > 100 ? content.substring(0, 100) + "..." : content);
    }
}
