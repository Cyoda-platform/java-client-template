```java
package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest; 
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class UserSubmitsRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserSubmitsRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public UserSubmitsRequestProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        
        // Check the status of the adoption request and update accordingly
        if ("PENDING".equals(entity.getStatus())) {
            // Logic to approve or reject the adoption request can be implemented.
            // For example, if we assume a simple approval mechanism:
            // You might want to check some conditions here before approval.
            entity.setStatus("APPROVED"); // Assuming we approve the request for this example

            // Optionally, update the status of the pet
            // Fetch the pet first
            CompletableFuture<DataPayload> petFuture = entityService.getItem(
                Pet.ENTITY_NAME,
                Pet.ENTITY_VERSION,
                UUID.fromString(entity.getPetId())
            );
            petFuture.thenAccept(petPayload -> {
                Pet pet = objectMapper.convertValue(petPayload.getData(), Pet.class);
                pet.setStatus("ADOPTED"); // Update pet status to adopted
                entityService.updateItem(UUID.fromString(pet.getId()), pet); // Update pet in the database
            });

            // Update the user's adoption requests list
            // This is a placeholder for the logic that would add the adoption request ID to the user's requests
            // This assumes you will have the user ID available, similar to the pet one
            // Fetch user first to update their adoption requests list (not implemented here)
        }
        
        return entity;
    }
}
```