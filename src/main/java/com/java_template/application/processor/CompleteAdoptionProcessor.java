package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class CompleteAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompleteAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CompleteAdoptionProcessor(SerializerFactory serializerFactory,
                                     EntityService entityService,
                                     ObjectMapper objectMapper) {
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

        // Only complete adoption for requests that are in approved state
        if (entity.getStatus() == null || !entity.getStatus().equalsIgnoreCase("approved")) {
            logger.info("AdoptionRequest {} is not in 'approved' state (current: {}). Skipping completion.", entity.getId(), entity.getStatus());
            return entity;
        }

        // Fetch the Pet referenced by this adoption request
        if (entity.getPetId() == null || entity.getPetId().isBlank()) {
            logger.warn("AdoptionRequest {} has no petId. Cannot complete adoption.", entity.getId());
            return entity;
        }

        try {
            CompletableFuture<ObjectNode> petFuture = entityService.getItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                UUID.fromString(entity.getPetId())
            );

            ObjectNode petItem = petFuture.get();
            if (petItem == null) {
                logger.warn("Pet with id {} not found for AdoptionRequest {}.", entity.getPetId(), entity.getId());
                return entity;
            }

            // The entityService.getItem typically returns an envelope { technicalId, entity: { ... } }
            ObjectNode petEntityNode = petItem.has("entity") && petItem.get("entity").isObject()
                ? (ObjectNode) petItem.get("entity")
                : petItem;

            Pet pet = objectMapper.treeToValue(petEntityNode, Pet.class);
            if (pet == null) {
                logger.warn("Failed to deserialize Pet entity for id {}.", entity.getPetId());
                return entity;
            }

            // Only change pet status if not already adopted
            if (pet.getStatus() == null || !pet.getStatus().equalsIgnoreCase("adopted")) {
                pet.setStatus("adopted");

                // Update pet using entity service (allowed: updating other entities)
                CompletableFuture<java.util.UUID> updateFuture = entityService.updateItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    UUID.fromString(entity.getPetId()),
                    pet
                );

                // Wait for update to complete to ensure consistency before marking the request completed
                updateFuture.get();
                logger.info("Pet {} status updated to 'adopted' for AdoptionRequest {}.", entity.getPetId(), entity.getId());
            } else {
                logger.info("Pet {} was already in 'adopted' status.", entity.getPetId());
            }

            // Mark adoption request as completed
            entity.setStatus("completed");
            logger.info("AdoptionRequest {} marked as 'completed'.", entity.getId());

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while completing adoption for request {}: {}", entity.getId(), ie.getMessage(), ie);
        } catch (ExecutionException ee) {
            logger.error("Error while completing adoption for request {}: {}", entity.getId(), ee.getMessage(), ee);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid UUID for petId {} in AdoptionRequest {}: {}", entity.getPetId(), entity.getId(), iae.getMessage(), iae);
        } catch (Exception ex) {
            logger.error("Unexpected error while completing adoption for request {}: {}", entity.getId(), ex.getMessage(), ex);
        }

        return entity;
    }
}