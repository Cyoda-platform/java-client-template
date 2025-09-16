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

/**
 * EggAlarmCreationProcessor - Initializes new egg alarm with cooking time
 * 
 * This processor handles the automatic transition from initial_state to created.
 * It sets the appropriate cooking time based on the selected egg type:
 * - SOFT_BOILED: 4 minutes
 * - MEDIUM_BOILED: 6 minutes  
 * - HARD_BOILED: 8 minutes
 */
@Component
public class EggAlarmCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EggAlarmCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EggAlarmCreationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EggAlarm creation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(EggAlarm.class)
                .validate(this::isValidEntityWithMetadata, "Invalid EggAlarm entity wrapper")
                .map(this::processEggAlarmCreation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for EggAlarm creation
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EggAlarm> entityWithMetadata) {
        EggAlarm entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        
        if (entity == null || technicalId == null) {
            return false;
        }
        
        // Validate required fields for creation
        return entity.getId() != null && !entity.getId().trim().isEmpty() &&
               entity.getEggType() != null && !entity.getEggType().trim().isEmpty() &&
               entity.getCreatedAt() != null &&
               isValidEggType(entity.getEggType());
    }

    /**
     * Main business logic for EggAlarm creation
     */
    private EntityWithMetadata<EggAlarm> processEggAlarmCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EggAlarm> context) {

        EntityWithMetadata<EggAlarm> entityWithMetadata = context.entityResponse();
        EggAlarm entity = entityWithMetadata.entity();

        logger.debug("Creating EggAlarm: {} with egg type: {}", entity.getId(), entity.getEggType());

        // Set cooking time based on egg type
        Integer cookingTime = getCookingTimeForEggType(entity.getEggType());
        entity.setCookingTimeMinutes(cookingTime);

        logger.info("EggAlarm {} created successfully with cooking time: {} minutes", 
                   entity.getId(), cookingTime);

        return entityWithMetadata;
    }

    /**
     * Validates if the egg type is one of the allowed values
     */
    private boolean isValidEggType(String eggType) {
        return "SOFT_BOILED".equals(eggType) || 
               "MEDIUM_BOILED".equals(eggType) || 
               "HARD_BOILED".equals(eggType);
    }

    /**
     * Returns the cooking time in minutes for the given egg type
     */
    private Integer getCookingTimeForEggType(String eggType) {
        switch (eggType) {
            case "SOFT_BOILED":
                return 4;
            case "MEDIUM_BOILED":
                return 6;
            case "HARD_BOILED":
                return 8;
            default:
                throw new IllegalArgumentException("Invalid egg type: " + eggType);
        }
    }
}
