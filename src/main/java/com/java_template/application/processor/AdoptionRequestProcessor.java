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
        
        // Fetch Pet by petId to update its status
        CompletableFuture<DataPayload> petFuture = entityService.getItem(
            Pet.ENTITY_NAME,
            Pet.ENTITY_VERSION,
            UUID.fromString(entity.getPetId())
        );
        
        try {
            DataPayload petDataPayload = petFuture.get();
            Pet pet = objectMapper.treeToValue(petDataPayload.getData(), Pet.class);
            
            // Check if pet is available for adoption
            if (pet.getStatus().equals("available")) {
                // Update pet status to PENDING_ADOPTION
                pet.setStatus("PENDING_ADOPTION");
                entityService.updateItem(UUID.fromString(pet.getId()), pet);
                
                // Additional business logic can be implemented here
                logger.info("Updated pet status to PENDING_ADOPTION for petId: {}", pet.getId());
            } else {
                logger.error("Pet with id {} is not available for adoption.", pet.getId());
            }
        } catch (Exception e) {
            logger.error("Error processing entity logic for adoption: {}", e.getMessage());
        }

        return entity;
    }
}
```