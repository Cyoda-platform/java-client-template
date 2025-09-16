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
 * EggAlarmCompletionProcessor - Completes the egg alarm when cooking time has elapsed
 * 
 * This processor handles the automatic transition from active to completed.
 * It is triggered by the EggAlarmTimerCriterion when the cooking time has elapsed.
 */
@Component
public class EggAlarmCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EggAlarmCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EggAlarmCompletionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EggAlarm completion for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EggAlarm.class)
                .validate(this::isValidEntityWithMetadata, "Invalid EggAlarm entity wrapper for completion")
                .map(this::processEggAlarmCompletion)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for EggAlarm completion
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EggAlarm> entityWithMetadata) {
        EggAlarm entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        
        if (entity == null || technicalId == null) {
            return false;
        }
        
        // Validate entity is in ACTIVE state and has required fields
        return "active".equals(currentState) &&
               entity.isValid() &&
               entity.getStartedAt() != null &&
               entity.getCookingTimeMinutes() != null &&
               entity.getCookingTimeMinutes() > 0;
    }

    /**
     * Main business logic for EggAlarm completion
     */
    private EntityWithMetadata<EggAlarm> processEggAlarmCompletion(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EggAlarm> context) {

        EntityWithMetadata<EggAlarm> entityWithMetadata = context.entityResponse();
        EggAlarm entity = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Completing EggAlarm: {} in state: {}", entity.getId(), currentState);

        // Set the completion timestamp
        LocalDateTime completionTime = LocalDateTime.now();
        entity.setCompletedAt(completionTime);

        // Calculate actual cooking time for logging
        if (entity.getStartedAt() != null) {
            long actualCookingTimeMinutes = ChronoUnit.MINUTES.between(entity.getStartedAt(), completionTime);
            logger.info("Egg alarm completed for {} after {} minutes", 
                       entity.getEggType(), actualCookingTimeMinutes);
        } else {
            logger.info("Egg alarm completed for {}", entity.getEggType());
        }

        // Log notification/alert that egg is ready
        logger.info("🥚 ALARM: Your {} egg is ready! 🥚", entity.getEggType().toLowerCase().replace("_", "-"));

        return entityWithMetadata;
    }
}
