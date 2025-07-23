package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
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

@Component
public class PetCreationJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetCreationJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetCreationJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetCreationJob for request: {}", request.getId());

        // Extract Pet entity from request
        Pet pet = serializer.extractEntity(request, Pet.class);

        // Perform processing as per functional requirements:
        // 1. Validation of pet data is assumed done by workflow criteria, so here just a simple check
        if (pet == null || !pet.isValid()) {
            logger.error("Invalid pet data in PetCreationJobProcessor");
            // Return error response (using serializer's response builder)
            return serializer.responseBuilder(request)
                    .withError("Invalid pet data")
                    .build();
        }

        // 2. Business logic simulation: Log processing (actual logic could be more complex)
        logger.info("Processing Pet with ID: {} and name: {}", pet.getPetId(), pet.getName());

        // 3. Mark pet status as AVAILABLE if not set
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("AVAILABLE");
        }

        // 4. Return successful response with processed entity
        return serializer.withRequest(request)
                .toEntity(Pet.class)
                .map(entity -> {
                    // Updating entity with any processing logic if needed
                    return pet;
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetCreationJobProcessor".equals(modelSpec.operationName()) &&
               "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    // Business processing logic could be added here if needed
}
