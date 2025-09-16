package com.java_template.application.processor;

import com.java_template.application.entity.eggalarm.version_1.EggAlarm;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * EggAlarmCancellationProcessor - Cancels an egg alarm
 * 
 * This processor handles the manual transition from created or active to cancelled.
 * It can cancel alarms either before they start (CREATED state) or while they're running (ACTIVE state).
 */
@Component
public class EggAlarmCancellationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EggAlarmCancellationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EggAlarmCancellationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EggAlarm cancellation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EggAlarm.class)
                .validate(this::isValidEntityWithMetadata, "Invalid EggAlarm entity wrapper for cancellation")
                .map(this::processEggAlarmCancellation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for EggAlarm cancellation
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EggAlarm> entityWithMetadata) {
        EggAlarm entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        
        if (entity == null || technicalId == null) {
            return false;
        }
        
        // Validate entity is in CREATED or ACTIVE state
        return ("created".equals(currentState) || "active".equals(currentState)) &&
               entity.isValid();
    }

    /**
     * Main business logic for EggAlarm cancellation
     */
    private EntityWithMetadata<EggAlarm> processEggAlarmCancellation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EggAlarm> context) {

        EntityWithMetadata<EggAlarm> entityWithMetadata = context.entityResponse();
        EggAlarm entity = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Cancelling EggAlarm: {} in state: {}", entity.getId(), currentState);

        // Handle cancellation based on current state
        if ("active".equals(currentState)) {
            // Calculate partial cooking time if alarm was active
            if (entity.getStartedAt() != null) {
                LocalDateTime now = LocalDateTime.now();
                long partialCookingTimeMinutes = ChronoUnit.MINUTES.between(entity.getStartedAt(), now);
                logger.info("Egg alarm cancelled for {} after {} minutes of cooking", 
                           entity.getEggType(), partialCookingTimeMinutes);
            } else {
                logger.info("Egg alarm cancelled for {} while active", entity.getEggType());
            }
        } else if ("created".equals(currentState)) {
            logger.info("Egg alarm cancelled for {} before starting", entity.getEggType());
        }

        // Clear any active timers or notifications (logged for demonstration)
        logger.debug("Clearing any active timers or notifications for EggAlarm: {}", entity.getId());

        return entityWithMetadata;
    }
}
