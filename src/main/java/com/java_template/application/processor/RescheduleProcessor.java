package com.java_template.application.processor;

import com.java_template.application.entity.notification.version_1.Notification;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class RescheduleProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RescheduleProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public RescheduleProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Reschedule for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Notification.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Notification entity) {
        return entity != null && entity.isValid();
    }

    private Notification processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Notification> context) {
        Notification notification = context.entity();
        try {
            // Transition rescheduled -> pending
            if ("RESCHEDULED".equalsIgnoreCase(notification.getState())) {
                notification.setState("PENDING");
                logger.info("Notification {} moved from RESCHEDULED to PENDING", notification.getId());

                // persist update
                try {
                    CompletableFuture<UUID> fut = entityService.updateItem(
                        Notification.ENTITY_NAME,
                        String.valueOf(Notification.ENTITY_VERSION),
                        UUID.fromString(notification.getTechnicalId()),
                        notification
                    );
                    fut.get();
                } catch (Exception ex) {
                    logger.warn("Failed to persist notification {} during reschedule: {}", notification.getId(), ex.getMessage());
                }
            } else {
                logger.debug("Notification {} in state {}; no reschedule action taken", notification.getId(), notification.getState());
            }
            return notification;
        } catch (Exception ex) {
            logger.error("Error in RescheduleProcessor for notification {}: {}", notification != null ? notification.getId() : null, ex.getMessage(), ex);
            return notification;
        }
    }
}
