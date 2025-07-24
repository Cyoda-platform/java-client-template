package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.ArrayList;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        logger.info("PetProcessor initialized with SerializerFactory, EntityService, and ObjectMapper");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid Pet entity")
            .map(this::processPetLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
               "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Pet processPetLogic(Pet pet) {
        // Business logic for processing Pet
        // Step 1: Initial State: Pet entity created with AVAILABLE status (assumed already set)

        // Step 2: Validation: Confirm required pet fields are present and valid
        if (!pet.isValid()) {
            logger.warn("Pet entity validation failed for id: {}", pet.getId());
            // Optionally throw exception or handle error
            return pet;
        }

        // Step 3: Indexing/Enrichment: Add tags or categorize pet if needed
        if (pet.getTags() == null) {
            pet.setTags(new ArrayList<>());
        }
        if (!pet.getTags().contains("indexed")) {
            pet.getTags().add("indexed");
        }

        // Step 4: Completion: Status remains AVAILABLE (or updated via PetStatusUpdate)
        // No changes needed here

        return pet;
    }

    private boolean isValidEntity(Pet pet) {
        return pet != null && pet.getId() != null && !pet.getId().isBlank() &&
               pet.getName() != null && !pet.getName().isBlank() &&
               pet.getCategory() != null && !pet.getCategory().isBlank() &&
               pet.getStatus() != null && !pet.getStatus().isBlank();
    }
}
