package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

@Component
public class ReservePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReservePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ReservePetProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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
        AdoptionRequest request = context.entity();

        // Default to failing reservation unless succeeded below
        try {
            String petTechnicalId = request.getPetId();
            if (petTechnicalId == null || petTechnicalId.isBlank()) {
                logger.warn("AdoptionRequest {} missing petId, marking as RESERVE_FAILED", request.getId());
                request.setStatus("RESERVE_FAILED");
                return request;
            }

            // Fetch the pet entity (may throw if id not UUID format)
            CompletableFuture<ObjectNode> petFuture = entityService.getItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                UUID.fromString(petTechnicalId)
            );

            ObjectNode petNode = petFuture.join();
            if (petNode == null || petNode.isEmpty()) {
                logger.info("Pet not found for id {}. Marking AdoptionRequest {} as RESERVE_FAILED", petTechnicalId, request.getId());
                request.setStatus("RESERVE_FAILED");
                return request;
            }

            Pet pet = objectMapper.treeToValue(petNode, Pet.class);
            if (pet == null) {
                logger.info("Failed to deserialize Pet for id {}. Marking AdoptionRequest {} as RESERVE_FAILED", petTechnicalId, request.getId());
                request.setStatus("RESERVE_FAILED");
                return request;
            }

            String petStatus = pet.getStatus();
            if (petStatus == null || !petStatus.equalsIgnoreCase("available")) {
                logger.info("Pet {} status is not available (status={}). Marking AdoptionRequest {} as RESERVE_FAILED", pet.getId(), petStatus, request.getId());
                request.setStatus("RESERVE_FAILED");
                return request;
            }

            // Pet is available -> reserve it by marking as pending and linking reservation
            pet.setStatus("pending");

            // Link reservation by adding a tag with the reservation id if tags supported
            List<String> tags = pet.getTags();
            if (tags == null) {
                tags = new ArrayList<>();
            }
            String reservationTag = "reservation:" + request.getId();
            if (!tags.contains(reservationTag)) {
                tags.add(reservationTag);
            }
            pet.setTags(tags);

            // Update the Pet entity (allowed since it's a different entity)
            try {
                CompletableFuture<UUID> updateFuture = entityService.updateItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    UUID.fromString(pet.getId()),
                    pet
                );
                updateFuture.join();
                // Update successful, mark request as RESERVED
                request.setStatus("RESERVED");
                logger.info("Successfully reserved Pet {} for AdoptionRequest {}", pet.getId(), request.getId());
            } catch (Exception e) {
                logger.error("Failed to update Pet {} while reserving for AdoptionRequest {}: {}", pet.getId(), request.getId(), e.getMessage(), e);
                request.setStatus("RESERVE_FAILED");
            }

        } catch (IllegalArgumentException iae) {
            // UUID parsing error or similar
            logger.warn("Invalid UUID provided in AdoptionRequest {}: {}. Marking as RESERVE_FAILED", request.getId(), request.getPetId());
            request.setStatus("RESERVE_FAILED");
        } catch (Exception e) {
            logger.error("Unexpected error while processing AdoptionRequest {}: {}", request.getId(), e.getMessage(), e);
            request.setStatus("RESERVE_FAILED");
        }

        return request;
    }
}