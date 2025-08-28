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
public class ValidateAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public ValidateAdoptionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
        Adoption adoption = context.entity();
        
        // Validate the existence of User and Pet
        CompletableFuture<DataPayload> userFuture = entityService.getItem(
            User.ENTITY_NAME,
            User.ENTITY_VERSION,
            UUID.fromString(adoption.getUserId())
        );
        
        CompletableFuture<DataPayload> petFuture = entityService.getItem(
            Pet.ENTITY_NAME,
            Pet.ENTITY_VERSION,
            UUID.fromString(adoption.getPetId())
        );

        CompletableFuture.allOf(userFuture, petFuture).join();

        DataPayload userPayload = userFuture.get();
        DataPayload petPayload = petFuture.get();

        if (userPayload == null || petPayload == null) {
            logger.error("User or Pet not found for adoptionId: {}", adoption.getId());
            throw new RuntimeException("User or Pet not found");
        }

        // Check if the pet is available for adoption
        Pet pet = petPayload.getData().traverse(Pet.class);
        if (!"available".equals(pet.getStatus())) {
            logger.error("Pet is not available for adoption. Pet ID: {}", pet.getId());
            throw new RuntimeException("Pet is not available for adoption");
        }

        // Update adoption status
        adoption.setStatus("validated");

        // Persist the updated adoption status
        CompletableFuture<UUID> updatedId = entityService.updateItem(
            UUID.fromString(adoption.getId()),
            adoption
        );
        
        UUID entityId = updatedId.get();
        logger.info("Adoption validated and updated with ID: {}", entityId);

        return adoption;
    }
}
```