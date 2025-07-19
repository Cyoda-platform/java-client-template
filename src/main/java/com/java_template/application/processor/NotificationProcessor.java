package com.java_template.application.processor;

import com.java_template.application.entity.Notification;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class NotificationProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public NotificationProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("NotificationProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Notification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Notification.class)
            .validate(this::isValidEntity, "Invalid Notification entity state")
            .map(this::processNotificationLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "NotificationProcessor".equals(modelSpec.operationName()) &&
               "notification".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(Notification notification) {
        return notification != null && notification.isValid();
    }

    private Notification processNotificationLogic(Notification notification) {
        // Step 1: Send email to subscriber
        logger.info("Sending email to subscriber: {} for notification: {}", notification.getSubscriberId(), notification.getId());

        boolean emailSent = sendEmail(notification);

        // Step 2: Update status and sentAt timestamp
        if (emailSent) {
            notification.setStatus(Notification.StatusEnum.SENT);
        } else {
            notification.setStatus(Notification.StatusEnum.FAILED);
        }
        notification.setSentAt(OffsetDateTime.now());

        return notification;
    }

    private boolean sendEmail(Notification notification) {
        // Simulate email sending logic
        // In real scenario, integrate with email service
        logger.info("Email sent to subscriberId: {}", notification.getSubscriberId());
        return true; // simulate success
    }
}
