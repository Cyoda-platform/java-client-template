```java
package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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
public class AdoptionRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AdoptionRequestProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
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

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest entity = context.entity();
        logger.info("Processing AdoptionRequest with ID: {}", entity.getId());

        // Retrieve the pet based on the petId in the adoption request
        CompletableFuture<DataPayload> petFuture = entityService.getItem(
                Pet.ENTITY_NAME,
                Pet.ENTITY_VERSION,
                UUID.fromString(entity.getPetId())
        );

        // Retrieve the user based on the userId in the adoption request
        CompletableFuture<DataPayload> userFuture = entityService.getItem(
                User.ENTITY_NAME,
                User.ENTITY_VERSION,
                UUID.fromString(entity.getUserId())
        );

        // Combine both futures and process them
        CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(petFuture, userFuture);
        combinedFuture.join();

        DataPayload petDataPayload = petFuture.join();
        DataPayload userDataPayload = userFuture.join();

        if (petDataPayload != null && userDataPayload != null) {
            // Logic for processing the adoption request
            // Example: Check pet status and update the adoption request status accordingly
            String petStatus = petDataPayload.getData().get("status").asText();
            if ("available".equalsIgnoreCase(petStatus)) {
                // Update pet status to adopted
                petDataPayload.getData().put("status", "adopted");
                entity.setStatus("approved");

                // Perform the update operation on the pet entity
                CompletableFuture<UUID> updatedPetIdFuture = entityService.updateItem(
                        UUID.fromString(entity.getPetId()),
                        objectMapper.convertValue(petDataPayload.getData(), Pet.class)
                );
                updatedPetIdFuture.join();
            } else {
                entity.setStatus("rejected");
            }
        }

        return entity;
    }
}
```