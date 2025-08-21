package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.eggtimer.version_1.EggTimer;
import com.java_template.application.entity.notification.version_1.Notification;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ScheduleNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ScheduleNotificationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ScheduleNotification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EggTimer.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(EggTimer entity) {
        return entity != null && entity.isValid();
    }

    private EggTimer processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<EggTimer> context) {
        EggTimer timer = context.entity();
        try {
            String notifyAt = timer.getExpectedEndAt();
            if (notifyAt == null) {
                logger.warn("Timer {} has no expectedEndAt; skipping notification scheduling", timer.getId());
                return timer;
            }

            // Idempotent check: look for existing notification for this timer at the same notifyAt
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.timerId", "EQUALS", timer.getId()),
                Condition.of("$.notifyAt", "EQUALS", notifyAt)
            );

            CompletableFuture<ObjectNode> itemsFuture = entityService.getItemsByCondition(
                Notification.ENTITY_NAME,
                String.valueOf(Notification.ENTITY_VERSION),
                condition,
                true
            );

            ObjectNode found = null;
            try {
                found = itemsFuture.get().size() > 0 ? (ObjectNode) itemsFuture.get().get(0) : null;
            } catch (Exception ex) {
                logger.warn("Error checking existing notifications for timer {}: {}", timer.getId(), ex.getMessage());
            }

            if (found == null) {
                Notification notif = new Notification();
                notif.setTimerId(timer.getId());
                notif.setUserId(timer.getOwnerUserId());
                notif.setNotifyAt(notifyAt);
                notif.setMethod(timer.getMetadata() != null && timer.getMetadata().has("preferredMethod") ? timer.getMetadata().get("preferredMethod").asText() : "alarm");
                notif.setDelivered(false);
                notif.setDeliveryAttempts(0);
                notif.setSnoozeCount(0);
                notif.setState("PENDING");

                try {
                    CompletableFuture<UUID> future = entityService.addItem(
                        Notification.ENTITY_NAME,
                        String.valueOf(Notification.ENTITY_VERSION),
                        notif
                    );
                    future.get();
                    logger.info("Created notification for timer {} at {}", timer.getId(), notifyAt);
                } catch (Exception ex) {
                    logger.error("Failed to create notification for timer {}: {}", timer.getId(), ex.getMessage(), ex);
                }
            } else {
                logger.info("Notification already exists for timer {} at {}", timer.getId(), notifyAt);
            }

            return timer;
        } catch (Exception ex) {
            logger.error("Error in ScheduleNotificationProcessor for timer {}: {}", timer != null ? timer.getId() : null, ex.getMessage(), ex);
            return timer;
        }
    }
}
