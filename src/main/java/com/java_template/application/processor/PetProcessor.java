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

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    // Simulate PetEvent id counter and cache as static for demonstration
    private static final AtomicInteger petEventIdCounter = new AtomicInteger(1);
    // Since we cannot use other services, simulate cache with a simple map if needed (not implemented here)

    public PetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer(); //always follow this pattern
        logger.info("PetProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(Pet.class)
                .validate(Pet::isValid, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
                "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Pet processEntityLogic(Pet pet) {
        logger.info("Processing Pet with ID: {}", pet.getId());

        if (!pet.isValid()) {
            logger.error("Pet validation failed for ID: {}", pet.getId());
            return pet;
        }

        if ("cat".equalsIgnoreCase(pet.getCategory())) {
            pet.setName(pet.getName() + " the Purrfect");
        }

        logger.info("Pet {} is currently {}", pet.getId(), pet.getStatus());

        // Simulate PetEvent creation and processing
        PetEvent petEvent = new PetEvent();
        petEvent.setId("PE" + petEventIdCounter.getAndIncrement());
        petEvent.setEventId(petEvent.getId());
        petEvent.setPetId(pet.getId());
        petEvent.setEventType("CREATED");
        petEvent.setTimestamp(LocalDateTime.now());
        petEvent.setStatus("RECORDED");
        // Note: petEventCache is not accessible here, so we omit caching

        // Simulate processPetEvent call - no implementation as per instructions

        return pet;
    }
}
