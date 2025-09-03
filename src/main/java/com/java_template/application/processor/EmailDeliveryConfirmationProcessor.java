package com.java_template.application.processor;

import com.java_template.application.entity.emailnotification.version_1.EmailNotification;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class EmailDeliveryConfirmationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailDeliveryConfirmationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public EmailDeliveryConfirmationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailNotification delivery confirmation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailNotification.class)
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

    private boolean isValidEntity(EmailNotification entity) {
        return entity != null && entity.isValid();
    }

    private EmailNotification processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<EmailNotification> context) {
        EmailNotification entity = context.entity();

        logger.info("Confirming email delivery for: {}", entity.getRecipientEmail());

        try {
            // Check email delivery status via SMTP response
            DeliveryStatus deliveryStatus = checkDeliveryStatus(entity);

            switch (deliveryStatus) {
                case DELIVERED:
                    entity.setDeliveryStatus("DELIVERED");
                    entity.setErrorMessage(null);
                    logger.info("Email delivery confirmed for: {}", entity.getRecipientEmail());
                    break;
                    
                case PENDING:
                    // Keep in 'sent' state, will recheck later
                    logger.info("Email delivery still pending for: {}", entity.getRecipientEmail());
                    // In a real implementation, this would schedule a recheck
                    break;
                    
                case FAILED:
                    entity.setDeliveryStatus("FAILED");
                    entity.setErrorMessage("Delivery confirmation failed");
                    logger.warn("Email delivery failed for: {}", entity.getRecipientEmail());
                    break;
            }

        } catch (Exception e) {
            logger.error("Error checking delivery status for {}: {}", entity.getRecipientEmail(), e.getMessage());
            entity.setErrorMessage("Delivery check error: " + e.getMessage());
        }

        return entity;
    }

    private DeliveryStatus checkDeliveryStatus(EmailNotification email) {
        // Simulate delivery status checking
        logger.info("Checking delivery status via SMTP response for: {}", email.getRecipientEmail());

        // Check if email was sent recently
        if (email.getActualSendTime() == null) {
            logger.warn("Email has no send time, cannot check delivery");
            return DeliveryStatus.FAILED;
        }

        // Calculate time since send
        long minutesSinceSend = ChronoUnit.MINUTES.between(email.getActualSendTime(), LocalDateTime.now());

        // Simulate delivery confirmation logic
        if (minutesSinceSend < 1) {
            // Too soon to confirm delivery
            return DeliveryStatus.PENDING;
        } else if (minutesSinceSend < 60) {
            // Within reasonable delivery time
            return simulateDeliveryConfirmation(email);
        } else {
            // Too long, assume failed
            logger.warn("Email delivery timeout for: {}", email.getRecipientEmail());
            return DeliveryStatus.FAILED;
        }
    }

    private DeliveryStatus simulateDeliveryConfirmation(EmailNotification email) {
        // Simulate SMTP delivery confirmation
        // In a real implementation, this would:
        // 1. Check SMTP server logs
        // 2. Parse delivery receipts
        // 3. Check bounce messages
        // 4. Verify recipient server acceptance

        // For simulation, assume most emails are delivered successfully
        if (email.getRecipientEmail().contains("invalid") || 
            email.getRecipientEmail().contains("bounce")) {
            return DeliveryStatus.FAILED;
        }

        // Simulate occasional delivery failures
        if (email.getRecipientEmail().hashCode() % 20 == 0) {
            return DeliveryStatus.FAILED;
        }

        return DeliveryStatus.DELIVERED;
    }

    private enum DeliveryStatus {
        DELIVERED,
        PENDING,
        FAILED
    }
}
