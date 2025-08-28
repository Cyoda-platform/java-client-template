```java
package com.java_template.application.processor;

import com.java_template.application.entity.adoption.version_1.Adoption;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ConfirmAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ConfirmAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ConfirmAdoptionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Adoption for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Adoption.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Adoption entity) {
        return entity != null && entity.isValid();
    }

    private Adoption processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Adoption> context) {
        Adoption entity = context.entity();
        logger.info("Processing adoption for entity: {}", entity.getId());

        // Validate user and pet existence
        CompletableFuture<DataPayload> petFuture = entityService.getItem(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, UUID.fromString(entity.getPetId()));
        CompletableFuture<DataPayload> userFuture = entityService.getItem(User.ENTITY_NAME, User.ENTITY_VERSION, UUID.fromString(entity.getUserId()));

        petFuture.join(); // Wait for pet future
        userFuture.join(); // Wait for user future

        if (petFuture.isCompletedExceptionally() || userFuture.isCompletedExceptionally()) {
            logger.error("User or Pet does not exist for adoption: {}", entity);
            throw new IllegalStateException("User or Pet does not exist for adoption");
        }

        // Change pet status to ADOPTED
        CompletableFuture<Pet> petDataFuture = petFuture.thenApply(dataPayload -> {
            Pet pet = objectMapper.convertValue(dataPayload.getData(), Pet.class);
            pet.setStatus("ADOPTED");
            return pet;
        });

        petDataFuture.thenAccept(pet -> {
            try {
                entityService.updateItem(UUID.fromString(pet.getId()), pet).get();
            } catch (Exception e) {
                logger.error("Failed to update pet status to ADOPTED: {}", e.getMessage(), e);
            }
        });

        // Update adoption status to COMPLETED
        entity.setStatus("COMPLETED");
        CompletableFuture<UUID> updatedId = entityService.updateItem(UUID.fromString(entity.getId()), entity);
        updatedId.join();

        logger.info("Adoption confirmed and updated for entity: {}", entity.getId());
        return entity;
    }
}
```