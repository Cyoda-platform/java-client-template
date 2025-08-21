package com.java_template.application.processor;

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
public class TimerCompleteProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TimerCompleteProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public TimerCompleteProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing TimerComplete for request: {}", request.getId());

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
            if (timer.getState() == null) return timer;
            String state = timer.getState();
            if ("COMPLETED".equalsIgnoreCase(state) || "CANCELLED".equalsIgnoreCase(state)) {
                logger.info("Timer {} already terminal ({}), skipping completion processing", timer.getId(), state);
                return timer;
            }

            Instant now = Instant.now();
            String expectedStr = timer.getExpectedEndAt();
            if (expectedStr == null) {
                logger.warn("Timer {} has no expectedEndAt; cannot determine completion", timer.getId());
                return timer;
            }

            Instant expected = Instant.parse(expectedStr);
            if (!now.isBefore(expected) || now.equals(expected)) {
                // complete
                timer.setState("COMPLETED");
                logger.info("Timer {} marked COMPLETED", timer.getId());

                // ensure notification exists (idempotent)
                try {
                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.timerId", "EQUALS", timer.getId()),
                        Condition.of("$.notifyAt", "EQUALS", timer.getExpectedEndAt())
                    );

                    CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItemsByCondition(
                        Notification.ENTITY_NAME,
                        String.valueOf(Notification.ENTITY_VERSION),
                        condition,
                        true
                    );

                    com.fasterxml.jackson.databind.node.ArrayNode arr = itemsFuture.get();
                    if (arr == null || arr.size() == 0) {
                        // create missing notification
                        Notification notif = new Notification();
                        notif.setTimerId(timer.getId());
                        notif.setUserId(timer.getOwnerUserId());
                        notif.setNotifyAt(timer.getExpectedEndAt());
                        notif.setMethod(timer.getMetadata() != null && timer.getMetadata().has("preferredMethod") ? timer.getMetadata().get("preferredMethod").asText() : "alarm");
                        notif.setDelivered(false);
                        notif.setDeliveryAttempts(0);
                        notif.setSnoozeCount(0);
                        notif.setState("PENDING");

                        try {
                            CompletableFuture<UUID> fut = entityService.addItem(
                                Notification.ENTITY_NAME,
                                String.valueOf(Notification.ENTITY_VERSION),
                                notif
                            );
                            fut.get();
                            logger.info("Created missing notification for timer {} at {}", timer.getId(), timer.getExpectedEndAt());
                        } catch (Exception ex) {
                            logger.warn("Failed to persist created notification for timer {}: {}", timer.getId(), ex.getMessage());
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("Error ensuring notification exists for timer {}: {}", timer.getId(), ex.getMessage());
                }

                // Persist history note (implementation may use separate history entity)
                logger.info("Timer {} completion processed; history should be persisted by a dedicated processor", timer.getId());
            } else {
                logger.info("Timer {} not yet completed. expectedEndAt={}", timer.getId(), expectedStr);
            }

            return timer;
        } catch (Exception ex) {
            logger.error("Error in TimerCompleteProcessor for timer {}: {}", timer != null ? timer.getId() : null, ex.getMessage(), ex);
            return timer;
        }
    }
}
