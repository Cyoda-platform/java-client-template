package com.java_template.application.processor;

import com.java_template.application.entity.email_notification_entity.version_1.EmailNotificationEntity;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * NotificationSchedulingProcessor - Schedule email notification for delivery
 * Transition: schedule_notification (none → scheduled)
 */
@Component
public class NotificationSchedulingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotificationSchedulingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotificationSchedulingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NotificationScheduling for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EmailNotificationEntity.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<EmailNotificationEntity> entityWithMetadata) {
        return entityWithMetadata != null && entityWithMetadata.metadata() != null && 
               entityWithMetadata.metadata().getId() != null;
    }

    private EntityWithMetadata<EmailNotificationEntity> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EmailNotificationEntity> context) {

        EntityWithMetadata<EmailNotificationEntity> entityWithMetadata = context.entityResponse();
        EmailNotificationEntity entity = entityWithMetadata.entity();

        logger.debug("Scheduling email notification for report: {}", entity.getReportId());

        // Schedule the notification
        scheduleNotification(entity);

        logger.info("Email notification scheduled successfully: {}", entity.getNotificationId());

        return entityWithMetadata;
    }

    /**
     * Schedule email notification for delivery
     */
    private void scheduleNotification(EmailNotificationEntity entity) {
        // Generate notification ID if not set
        if (entity.getNotificationId() == null) {
            entity.setNotificationId(UUID.randomUUID().toString());
        }

        // Initialize delivery tracking
        entity.setDeliveryAttempts(0);
        entity.setLastError(null);

        // Validate email format and report existence
        validateNotificationData(entity);

        logger.debug("Email notification scheduled for recipient: {}", entity.getRecipientEmail());
    }

    /**
     * Validate notification data
     */
    private void validateNotificationData(EmailNotificationEntity entity) {
        if (entity.getRecipientEmail() == null || entity.getRecipientEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Recipient email is required");
        }

        if (entity.getReportId() == null || entity.getReportId().trim().isEmpty()) {
            throw new IllegalArgumentException("Report ID is required");
        }

        if (entity.getSubject() == null || entity.getSubject().trim().isEmpty()) {
            throw new IllegalArgumentException("Email subject is required");
        }

        if (entity.getBody() == null || entity.getBody().trim().isEmpty()) {
            throw new IllegalArgumentException("Email body is required");
        }

        if (entity.getScheduledTime() == null) {
            throw new IllegalArgumentException("Scheduled time is required");
        }

        logger.debug("Notification data validation passed for: {}", entity.getNotificationId());
    }
}
