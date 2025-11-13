package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
 * ABOUTME: Processor that fetches and initializes pet data when a pet entity is created.
 * Handles initial data population and timestamp setup.
 */
@Component
public class FetchPetDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchPetDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public FetchPetDataProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet data fetch for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Pet.class)
                .validate(this::isValidEntityWithMetadata, "Invalid pet entity wrapper")
                .map(this::processPetDataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Pet> entityWithMetadata) {
        Pet entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Pet> processPetDataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {

        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet pet = entityWithMetadata.entity();

        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing pet: {} in state: {}", pet.getPetId(), currentState);

        // Initialize timestamps if not set
        if (pet.getCreatedAt() == null) {
            pet.setCreatedAt(LocalDateTime.now());
        }
        pet.setUpdatedAt(LocalDateTime.now());

        // Set source if not already set
        if (pet.getSource() == null) {
            pet.setSource("pets_api");
        }

        logger.info("Pet {} data fetched and initialized successfully", pet.getPetId());

        return entityWithMetadata;
    }
}

