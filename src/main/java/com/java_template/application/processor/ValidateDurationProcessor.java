package com.java_template.application.processor;

import com.java_template.application.entity.eggtimer.version_1.EggTimer;
import com.java_template.application.entity.user.version_1.User;
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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Component
public class ValidateDurationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateDurationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ValidateDurationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ValidateDuration for request: {}", request.getId());

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
            // Ensure eggs count is sensible
            int eggs = timer.getEggsCount() != null ? timer.getEggsCount() : 1;
            if (eggs < 1) {
                logger.warn("Timer {} has invalid eggsCount={}; normalizing to 1", timer.getId(), timer.getEggsCount());
                eggs = 1;
                timer.setEggsCount(eggs);
            }

            // Compute duration if not provided or invalid
            Integer duration = timer.getDurationSeconds();
            if (duration == null || duration <= 0) {
                int base = lookupBaseDuration(timer.getBoilType(), timer.getEggSize());
                int adjustment = Math.max(0, eggs - 1) * 15; // per egg adjustment
                timer.setDurationSeconds(base + adjustment);
                logger.info("Computed durationSeconds={} for timerId={}", timer.getDurationSeconds(), timer.getId());
            }

            // Normalize startAt and determine scheduling
            Instant now = Instant.now();
            String startAtStr = timer.getStartAt();
            Instant startAt = null;
            if (startAtStr != null) {
                try {
                    startAt = Instant.parse(startAtStr);
                } catch (Exception ex) {
                    // invalid format - treat as immediate start
                    logger.warn("Invalid startAt format for timer {}: {}. Will start immediately.", timer.getId(), startAtStr);
                    startAt = null;
                }
            }

            if (startAt == null || !startAt.isAfter(now)) {
                // start immediately
                timer.setScheduledStartAt(now.toString());
                timer.setState("RUNNING");
                Instant expected = now.plusSeconds(timer.getDurationSeconds());
                timer.setExpectedEndAt(expected.toString());
                logger.info("Timer {} scheduled to RUNNING now, expectedEndAt={}", timer.getId(), timer.getExpectedEndAt());
            } else {
                // scheduled for future
                timer.setScheduledStartAt(startAt.toString());
                timer.setState("SCHEDULED");
                Instant expected = startAt.plusSeconds(timer.getDurationSeconds());
                timer.setExpectedEndAt(expected.toString());
                logger.info("Timer {} scheduled for future startAt={}, expectedEndAt={}", timer.getId(), timer.getScheduledStartAt(), timer.getExpectedEndAt());
            }

            // Enforce allowMultipleTimers rule if possible (best-effort):
            try {
                if (timer.getOwnerUserId() != null && !timer.getOwnerUserId().isBlank()) {
                    SearchConditionRequest userCond = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", timer.getOwnerUserId())
                    );
                    CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> userFuture = entityService.getItemsByCondition(
                        User.ENTITY_NAME,
                        String.valueOf(User.ENTITY_VERSION),
                        userCond,
                        true
                    );
                    com.fasterxml.jackson.databind.node.ArrayNode users = userFuture.get();
                    if (users != null && users.size() > 0) {
                        com.fasterxml.jackson.databind.JsonNode u = users.get(0);
                        boolean allowMultiple = true;
                        if (u.has("allowMultipleTimers")) {
                            allowMultiple = u.get("allowMultipleTimers").asBoolean(true);
                        }

                        if (!allowMultiple) {
                            // check existing active timers for this user
                            SearchConditionRequest tcond = SearchConditionRequest.group("AND",
                                Condition.of("$.ownerUserId", "EQUALS", timer.getOwnerUserId()),
                                Condition.of("$.state", "IN", "SCHEDULED,RUNNING")
                            );
                            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> timersFuture = entityService.getItemsByCondition(
                                EggTimer.ENTITY_NAME,
                                String.valueOf(EggTimer.ENTITY_VERSION),
                                tcond,
                                true
                            );
                            com.fasterxml.jackson.databind.node.ArrayNode existing = timersFuture.get();
                            if (existing != null && existing.size() > 0) {
                                // Business decision: mark timer as CANCELLED and log conflict
                                logger.warn("User {} disallows multiple timers and already has active timer. Marking new timer {} as CANCELLED.", timer.getOwnerUserId(), timer.getId());
                                timer.setState("CANCELLED");
                                return timer;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                logger.warn("Failed to enforce allowMultipleTimers rule for timer {}: {}", timer.getId(), ex.getMessage());
            }

            return timer;
        } catch (Exception ex) {
            logger.error("Error processing ValidateDurationProcessor for timer {}: {}", timer != null ? timer.getId() : null, ex.getMessage(), ex);
            return timer;
        }
    }

    private int lookupBaseDuration(String boilType, String eggSize) {
        String bt = (boilType != null) ? boilType.toLowerCase(Locale.ROOT) : "medium";
        String es = (eggSize != null) ? eggSize.toLowerCase(Locale.ROOT) : "medium";

        // Defaults based on functional requirements
        switch (bt) {
            case "soft":
                switch (es) {
                    case "small": return 180;
                    case "large": return 240;
                    default: return 210; // medium
                }
            case "hard":
                switch (es) {
                    case "small": return 300;
                    case "large": return 420;
                    default: return 360; // medium
                }
            case "medium":
            default:
                switch (es) {
                    case "small": return 240;
                    case "large": return 360;
                    default: return 300; // medium
                }
        }
    }
}
