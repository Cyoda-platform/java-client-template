```java
package com.java_template.application.processor;

import com.java_template.application.entity.adoption.version_1.Adoption;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.user.version_1.User;
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
public class ProcessAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProcessAdoptionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        String petId = entity.getPetId();
        String userId = entity.getUserId();

        // Validate user and pet existence
        CompletableFuture<DataPayload> petFuture = entityService.getItem(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, UUID.fromString(petId));
        CompletableFuture<DataPayload> userFuture = entityService.getItem(User.ENTITY_NAME, User.ENTITY_VERSION, UUID.fromString(userId));

        petFuture.join();  // Wait for the pet retrieval
        userFuture.join(); // Wait for the user retrieval

        DataPayload petPayload = petFuture.join();
        DataPayload userPayload = userFuture.join();

        if (petPayload == null || userPayload == null) {
            logger.error("User or Pet not found. UserId: {}, PetId: {}", userId, petId);
            throw new RuntimeException("User or Pet not found");
        }

        // Update adoption status to COMPLETED
        entity.setStatus("COMPLETED");

        // Notify the user about the successful adoption (This could involve calling an external service, etc.)
        notifyUser(userPayload);

        return entity;
    }

    private void notifyUser(DataPayload userPayload) {
        // Logic to notify user about the successful adoption
        String userEmail = userPayload.getData().get("email").asText();
        // Example: Send an email or push notification
        logger.info("Notifying user at email: {}", userEmail);
    }
}
```