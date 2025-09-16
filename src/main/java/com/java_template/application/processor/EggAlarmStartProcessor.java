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

/**
 * EggAlarmStartProcessor - Starts the egg cooking timer
 * 
 * This processor handles the manual transition from created to active.
 * It records the start time when the user starts the egg alarm.
 */
@Component
public class EggAlarmStartProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EggAlarmStartProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EggAlarmStartProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EggAlarm start for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EggAlarm.class)
                .validate(this::isValidEntityWithMetadata, "Invalid EggAlarm entity wrapper for start")
                .map(this::processEggAlarmStart)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for EggAlarm start
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EggAlarm> entityWithMetadata) {
        EggAlarm entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        
        if (entity == null || technicalId == null) {
            return false;
        }
        
        // Validate entity is in CREATED state and has required fields
        return "created".equals(currentState) &&
               entity.isValid() &&
               entity.getCookingTimeMinutes() != null &&
               entity.getCookingTimeMinutes() > 0;
    }

    /**
     * Main business logic for EggAlarm start
     */
    private EntityWithMetadata<EggAlarm> processEggAlarmStart(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EggAlarm> context) {

        EntityWithMetadata<EggAlarm> entityWithMetadata = context.entityResponse();
        EggAlarm entity = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Starting EggAlarm: {} in state: {}", entity.getId(), currentState);

        // Set the start timestamp
        LocalDateTime startTime = LocalDateTime.now();
        entity.setStartedAt(startTime);

        logger.info("Egg alarm started for {} cooking for {} minutes", 
                   entity.getEggType(), entity.getCookingTimeMinutes());

        return entityWithMetadata;
    }
}
