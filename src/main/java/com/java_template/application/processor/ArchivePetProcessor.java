package com.java_template.application.processor;

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ArchivePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchivePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ArchivePetProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Pet.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();
        if (entity == null) return null;

        String currentStatus = entity.getStatus();
        if (currentStatus == null) currentStatus = "";

        logger.info("ArchivePetProcessor invoked for petId={}, currentStatus={}", entity.getPetId(), currentStatus);

        // If already archived, nothing to do
        if ("Archived".equalsIgnoreCase(currentStatus)) {
            logger.info("Pet {} is already archived. No action taken.", entity.getPetId());
            return entity;
        }

        // Only allow archiving from Available or Adopted states
        if (!"Available".equalsIgnoreCase(currentStatus) && !"Adopted".equalsIgnoreCase(currentStatus)) {
            logger.warn("Pet {} cannot be archived from state '{}'. Allowed states: Available, Adopted", entity.getPetId(), currentStatus);
            // Annotate metadata with rejection reason (do not change status)
            try {
                Map<String, Object> metadata = entity.getMetadata();
                if (metadata != null) {
                    metadata.put("archiveRejectedReason", "Pet must be in Available or Adopted status to archive. Current status: " + currentStatus);
                    metadata.put("archiveRejectedAt", Instant.now().toString());
                }
            } catch (Exception ex) {
                logger.error("Failed to annotate metadata for pet {}: {}", entity.getPetId(), ex.getMessage(), ex);
            }
            return entity;
        }

        // Proceed to archive
        entity.setStatus("Archived");
        try {
            Map<String, Object> metadata = entity.getMetadata();
            if (metadata != null) {
                metadata.put("archivedAt", Instant.now().toString());
                metadata.put("archivedByProcessor", className);
            }
        } catch (Exception ex) {
            logger.warn("Failed to update metadata for pet {}: {}", entity.getPetId(), ex.getMessage());
        }

        // If pet was Adopted, ensure any user's adoptedPetIds are cleaned up.
        if ("Adopted".equalsIgnoreCase(currentStatus)) {
            try {
                CompletableFuture<List<DataPayload>> usersFuture = entityService.getItems(User.ENTITY_NAME, User.ENTITY_VERSION, null, null, null);
                List<DataPayload> userPayloads = usersFuture.get();
                if (userPayloads != null) {
                    for (DataPayload payload : userPayloads) {
                        try {
                            Object dataNode = payload.getData();
                            // Convert to User
                            User user = objectMapper.treeToValue((com.fasterxml.jackson.databind.JsonNode) dataNode, User.class);
                            if (user == null) continue;
                            List<String> adopted = user.getAdoptedPetIds();
                            if (adopted != null && entity.getPetId() != null && adopted.contains(entity.getPetId())) {
                                // remove petId and update user
                                adopted.remove(entity.getPetId());
                                user.setAdoptedPetIds(adopted);
                                String technicalId = null;
                                try {
                                    if (payload.getMeta() != null && payload.getMeta().get("entityId") != null) {
                                        technicalId = payload.getMeta().get("entityId").asText();
                                    }
                                } catch (Exception ex) {
                                    logger.warn("Unable to read technical id for user payload: {}", ex.getMessage());
                                }
                                if (technicalId != null && !technicalId.isBlank()) {
                                    try {
                                        CompletableFuture<UUID> updateFuture = entityService.updateItem(UUID.fromString(technicalId), user);
                                        UUID updatedId = updateFuture.get();
                                        logger.info("Updated user {} to remove adopted pet {}. updateId={}", user.getUserId(), entity.getPetId(), updatedId);
                                    } catch (Exception ex) {
                                        logger.error("Failed to update user {} while cleaning adoptedPetIds: {}", user.getUserId(), ex.getMessage(), ex);
                                    }
                                } else {
                                    logger.warn("Technical id missing for user {}, skipping update", user.getUserId());
                                }
                            }
                        } catch (Exception exInner) {
                            logger.error("Failed processing a user payload while archiving pet {}: {}", entity.getPetId(), exInner.getMessage(), exInner);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to fetch/update users while archiving pet {}: {}", entity.getPetId(), e.getMessage(), e);
            }
        }

        logger.info("Pet {} archived successfully.", entity.getPetId());
        return entity;
    }
}