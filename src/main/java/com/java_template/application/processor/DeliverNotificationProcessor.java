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

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DeliverNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliverNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public DeliverNotificationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DeliverNotification for request: {}", request.getId());

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
            String state = notification.getState();
            if ("DELIVERED".equalsIgnoreCase(state) && Boolean.TRUE.equals(notification.getDelivered())) {
                logger.info("Notification {} already delivered; skipping", notification.getId());
                return notification;
            }

            // mark delivering
            notification.setState("DELIVERING");
            notification.setDeliveryAttempts((notification.getDeliveryAttempts() == null ? 0 : notification.getDeliveryAttempts()) + 1);
            notification.setLastAttemptAt(Instant.now().toString());

            // Simulate delivery attempt (idempotent)
            boolean success = attemptDelivery(notification);

            if (success) {
                notification.setDelivered(true);
                notification.setState("DELIVERED");
                logger.info("Notification {} delivered successfully", notification.getId());
            } else {
                // treat transient failures as reschedule with backoff
                int attempts = notification.getDeliveryAttempts() == null ? 1 : notification.getDeliveryAttempts();
                if (isPermanentFailure(notification)) {
                    notification.setState("FAILED");
                    logger.info("Notification {} delivery failed permanently after {} attempts", notification.getId(), attempts);
                } else {
                    notification.setState("RESCHEDULED");
                    long backoffSeconds = computeBackoffSeconds(attempts);
                    Instant next = Instant.now().plusSeconds(backoffSeconds);
                    notification.setNotifyAt(next.toString());
                    logger.info("Notification {} scheduled for retry at {} (backoff {}s)", notification.getId(), next.toString(), backoffSeconds);
                }
            }

            // Persist changes to notification entity (update other entities allowed via entityService)
            try {
                CompletableFuture<UUID> fut = entityService.updateItem(
                    Notification.ENTITY_NAME,
                    String.valueOf(Notification.ENTITY_VERSION),
                    UUID.fromString(notification.getTechnicalId()),
                    notification
                );
                fut.get();
            } catch (Exception ex) {
                logger.warn("Failed to persist notification {} changes: {}", notification.getId(), ex.getMessage());
            }

            return notification;
        } catch (Exception ex) {
            logger.error("Error in DeliverNotificationProcessor for notification {}: {}", notification != null ? notification.getId() : null, ex.getMessage(), ex);
            return notification;
        }
    }

    private boolean attemptDelivery(Notification notification) {
        // In a real system this would call external channels. For prototype, deliver if method != "email" (simulate email failure)
        String method = notification.getMethod();
        if (method == null) method = "alarm";
        // Simple deterministic behavior for idempotency in tests
        return !"email".equalsIgnoreCase(method);
    }

    private boolean isPermanentFailure(Notification notification) {
        // treat email as permanent failure in prototype; also cap attempts at 3
        int attempts = notification.getDeliveryAttempts() == null ? 0 : notification.getDeliveryAttempts();
        return "email".equalsIgnoreCase(notification.getMethod()) || attempts >= 3;
    }

    private long computeBackoffSeconds(int attempts) {
        // exponential backoff base 60s
        return (long) Math.pow(2, Math.max(0, attempts - 1)) * 60L;
    }
}
