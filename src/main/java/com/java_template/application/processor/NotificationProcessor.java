package com.java_template.application.processor;

import com.java_template.application.entity.Notification;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
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
    private final EntityService entityService;

    public NotificationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .validate(Notification::isValid, "Invalid Notification state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "NotificationProcessor".equals(modelSpec.operationName()) &&
                "notification".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Notification processEntityLogic(Notification notification) {
        logger.info("Processing Notification with id: {}", notification.getId());

        try {
            Thread.sleep(50); // simulate delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Notification updatedNotification = new Notification();
        updatedNotification.setId(notification.getId());
        updatedNotification.setSubscriberId(notification.getSubscriberId());
        updatedNotification.setJobId(notification.getJobId());
        updatedNotification.setStatus(Notification.StatusEnum.SENT);
        updatedNotification.setSentAt(OffsetDateTime.now());

        try {
            entityService.addItem("Notification", Config.ENTITY_VERSION, updatedNotification).get();
        } catch (Exception e) {
            logger.error("Failed to update Notification status to SENT: {}", e.getMessage());
        }

        logger.info("Notification sent to subscriberId: {} for jobId: {}", notification.getSubscriberId(), notification.getJobId());

        return notification;
    }
}
