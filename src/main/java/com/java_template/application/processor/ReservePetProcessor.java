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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ReservePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReservePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReservePetProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        AdoptionRequest requestEntity = context.entity();
        logger.info("ReservePetProcessor - handling adoption request id={}, petId={}, ownerId={}",
            requestEntity.getId(), requestEntity.getPetId(), requestEntity.getOwnerId());

        // Attempt to read the Pet entity referenced by this adoption request.
        try {
            if (requestEntity.getPetId() == null || requestEntity.getPetId().isBlank()) {
                logger.warn("AdoptionRequest {} has no petId, marking as reserve_failed", requestEntity.getId());
                requestEntity.setStatus("reserve_failed");
                return requestEntity;
            }

            // Retrieve pet by technical id - follow existing conventions: convert to UUID
            UUID petTechnicalId;
            try {
                petTechnicalId = UUID.fromString(requestEntity.getPetId());
            } catch (IllegalArgumentException ex) {
                // petId is not a UUID string — try to fetch by id via search fallback is not provided here,
                // so mark request as reserve_failed to be safe.
                logger.warn("Pet id '{}' on AdoptionRequest {} is not a UUID. Marking request as reserve_failed.",
                    requestEntity.getPetId(), requestEntity.getId());
                requestEntity.setStatus("reserve_failed");
                return requestEntity;
            }

            CompletableFuture<ObjectNode> petFuture = entityService.getItem(
                Pet.ENTITY_NAME,
                String.valueOf(Pet.ENTITY_VERSION),
                petTechnicalId
            );

            ObjectNode petNode = petFuture == null ? null : petFuture.join();
            if (petNode == null || petNode.isEmpty()) {
                logger.warn("Pet not found for id {} referenced by AdoptionRequest {}. Marking as reserve_failed.",
                    requestEntity.getPetId(), requestEntity.getId());
                requestEntity.setStatus("reserve_failed");
                return requestEntity;
            }

            Pet pet = objectMapper.treeToValue(petNode, Pet.class);
            if (pet == null) {
                logger.warn("Unable to deserialize Pet for id {}. Marking AdoptionRequest {} as reserve_failed.",
                    requestEntity.getPetId(), requestEntity.getId());
                requestEntity.setStatus("reserve_failed");
                return requestEntity;
            }

            String petStatus = pet.getStatus();
            if (petStatus == null || !petStatus.equalsIgnoreCase("available")) {
                logger.info("Pet {} is not available (status={}). Marking AdoptionRequest {} as reserve_failed.",
                    pet.getId(), petStatus, requestEntity.getId());
                requestEntity.setStatus("reserve_failed");
                return requestEntity;
            }

            // Pet is available -> reserve it: set pet.status = pending and add a reservation tag linking request id.
            pet.setStatus("pending");

            // Link reservation: use tags to record reservation reference (existing property)
            List<String> tags = pet.getTags();
            if (tags == null) {
                tags = new ArrayList<>();
            }
            String reservationTag = "reserved_by:" + requestEntity.getId();
            if (!tags.contains(reservationTag)) {
                tags.add(reservationTag);
            }
            pet.setTags(tags);

            // Update pet entity via EntityService (allowed - updating other entities)
            try {
                CompletableFuture<UUID> updated = entityService.updateItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    petTechnicalId,
                    pet
                );
                // Wait for update to complete to ensure consistent state before advancing request.
                if (updated != null) {
                    updated.join();
                }
            } catch (Exception e) {
                logger.error("Failed to update Pet {} while reserving for AdoptionRequest {}. Rolling back request status.",
                    pet.getId(), requestEntity.getId(), e);
                requestEntity.setStatus("reserve_failed");
                return requestEntity;
            }

            // Mark adoption request as reserved
            requestEntity.setStatus("reserved");
            logger.info("Successfully reserved Pet {} for AdoptionRequest {}", pet.getId(), requestEntity.getId());
            return requestEntity;

        } catch (Exception e) {
            logger.error("Unexpected error while processing AdoptionRequest {}: {}", requestEntity.getId(), e.getMessage(), e);
            requestEntity.setStatus("reserve_failed");
            return requestEntity;
        }
    }
}