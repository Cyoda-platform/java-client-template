package com.java_template.application.processor;

import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.PetJob.StatusEnum;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class PetJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final com.java_template.common.service.EntityService entityService;

    public PetJobProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
        logger.info("PetJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetJob.class)
                .validate(PetJob::isValid)
                .map(entity -> {
                    try {
                        return processPetJob(entity);
                    } catch (ExecutionException | InterruptedException e) {
                        logger.error("Error processing PetJob", e);
                        entity.setStatus(StatusEnum.FAILED);
                        return entity;
                    }
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
                "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    // Actual business logic from processPetJob method in prototype
    private PetJob processPetJob(PetJob petJob) throws ExecutionException, InterruptedException {
        logger.info("Processing PetJob with technicalId: {}", petJob.getTechnicalId());
        if ("AddPet".equalsIgnoreCase(petJob.getType())) {
            Map<String, Object> payload = parsePayload(petJob.getPayload());
            if (payload == null) {
                logger.error("PetJob payload is null for AddPet");
                petJob.setStatus(StatusEnum.FAILED);
                return petJob;
            }
            String name = (String) payload.get("name");
            String category = (String) payload.get("category");
            if (name == null || name.isBlank() || category == null || category.isBlank()) {
                logger.error("Invalid Pet data in PetJob payload");
                petJob.setStatus(StatusEnum.FAILED);
                return petJob;
            }
            com.java_template.application.entity.Pet newPet = new com.java_template.application.entity.Pet();
            newPet.setName(name);
            newPet.setCategory(category);
            Object tagsObj = payload.get("tags");
            if (tagsObj instanceof List<?>) {
                List<String> tags = new ArrayList<>();
                for (Object o : (List<?>) tagsObj) {
                    if (o instanceof String) tags.add((String) o);
                }
                newPet.setTags(tags);
            }
            Object photosObj = payload.get("photoUrls");
            if (photosObj instanceof List<?>) {
                List<String> photos = new ArrayList<>();
                for (Object o : (List<?>) photosObj) {
                    if (o instanceof String) photos.add((String) o);
                }
                newPet.setPhotoUrls(photos);
            }
            newPet.setStatus(com.java_template.application.entity.Pet.StatusEnum.AVAILABLE);

            CompletableFuture<UUID> petIdFuture = entityService.addItem(com.java_template.application.entity.Pet.class.getSimpleName(), Config.ENTITY_VERSION, newPet);
            UUID petTechnicalId = petIdFuture.get();
            newPet.setTechnicalId(petTechnicalId);

            logger.info("Added new Pet with technicalId: {}", petTechnicalId);
            petJob.setStatus(StatusEnum.COMPLETED);
        } else if ("UpdatePetStatus".equalsIgnoreCase(petJob.getType())) {
            Map<String, Object> payload = parsePayload(petJob.getPayload());
            if (payload == null) {
                logger.error("PetJob payload is null for UpdatePetStatus");
                petJob.setStatus(StatusEnum.FAILED);
                return petJob;
            }
            String petIdStr = (String) payload.get("id");
            String newStatus = (String) payload.get("status");
            if (petIdStr == null || petIdStr.isBlank() || newStatus == null || newStatus.isBlank()) {
                logger.error("Invalid Pet ID or status in PetJob payload");
                petJob.setStatus(StatusEnum.FAILED);
                return petJob;
            }
            UUID petId;
            try {
                petId = UUID.fromString(petIdStr);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid UUID format for Pet id in PetJob payload: {}", petIdStr);
                petJob.setStatus(StatusEnum.FAILED);
                return petJob;
            }
            CompletableFuture<ObjectNode> existingPetFuture = entityService.getItem(com.java_template.application.entity.Pet.class.getSimpleName(), Config.ENTITY_VERSION, petId);
            ObjectNode existingPetNode = existingPetFuture.get();
            if (existingPetNode == null || existingPetNode.isEmpty()) {
                logger.error("Pet not found for update with technicalId: {}", petIdStr);
                petJob.setStatus(StatusEnum.FAILED);
                return petJob;
            }

            com.java_template.application.entity.Pet updatedPet = new com.java_template.application.entity.Pet();
            updatedPet.setName(existingPetNode.has("name") && !existingPetNode.get("name").isNull() ? existingPetNode.get("name").asText() : null);
            updatedPet.setCategory(existingPetNode.has("category") && !existingPetNode.get("category").isNull() ? existingPetNode.get("category").asText() : null);
            if (existingPetNode.has("tags") && existingPetNode.get("tags").isArray()) {
                List<String> tags = new ArrayList<>();
                existingPetNode.get("tags").forEach(node -> {
                    if (!node.isNull()) tags.add(node.asText());
                });
                updatedPet.setTags(tags);
            }
            if (existingPetNode.has("photoUrls") && existingPetNode.get("photoUrls").isArray()) {
                List<String> photos = new ArrayList<>();
                existingPetNode.get("photoUrls").forEach(node -> {
                    if (!node.isNull()) photos.add(node.asText());
                });
                updatedPet.setPhotoUrls(photos);
            }
            updatedPet.setStatus(newStatus);

            CompletableFuture<UUID> updatedPetIdFuture = entityService.addItem(com.java_template.application.entity.Pet.class.getSimpleName(), Config.ENTITY_VERSION, updatedPet);
            UUID updatedPetTechnicalId = updatedPetIdFuture.get();
            updatedPet.setTechnicalId(updatedPetTechnicalId);

            logger.info("Updated Pet status for new Pet technicalId: {}", updatedPetTechnicalId);
            petJob.setStatus(StatusEnum.COMPLETED);
        } else {
            logger.error("Unknown PetJob type: {}", petJob.getType());
            petJob.setStatus(StatusEnum.FAILED);
        }
        return petJob;
    }

    private Map<String, Object> parsePayload(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try {
            return objectMapper.readValue(payload, Map.class);
        } catch (Exception e) {
            logger.error("Failed to parse payload JSON", e);
            return null;
        }
    }
}
