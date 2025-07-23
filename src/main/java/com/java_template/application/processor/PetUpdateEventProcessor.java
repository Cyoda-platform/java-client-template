package com.java_template.application.processor;

import com.java_template.application.entity.PetUpdateEvent;
import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PetUpdateEventProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public PetUpdateEventProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetUpdateEventProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetUpdateEvent for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetUpdateEvent.class)
                .validate(this::isValidEntity, "Invalid PetUpdateEvent state")
                .map(this::processPetUpdateEvent)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetUpdateEventProcessor".equals(modelSpec.operationName()) &&
                "petUpdateEvent".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetUpdateEvent entity) {
        return entity != null && entity.isValid();
    }

    private PetUpdateEvent processPetUpdateEvent(PetUpdateEvent petUpdateEvent) {
        logger.info("Processing PetUpdateEvent with ID: {}", petUpdateEvent.getId());

        if (petUpdateEvent.getPetId() == null || petUpdateEvent.getPetId().isBlank()) {
            petUpdateEvent.setStatus("FAILED");
            logger.error("PetUpdateEvent validation failed: petId is blank");
            return petUpdateEvent;
        }
        if (petUpdateEvent.getUpdatedFields() == null || petUpdateEvent.getUpdatedFields().isEmpty()) {
            petUpdateEvent.setStatus("FAILED");
            logger.error("PetUpdateEvent validation failed: updatedFields empty");
            return petUpdateEvent;
        }

        UUID petTechnicalId;
        try {
            petTechnicalId = UUID.fromString(petUpdateEvent.getPetId());
        } catch (IllegalArgumentException e) {
            petUpdateEvent.setStatus("FAILED");
            logger.error("PetUpdateEvent processing failed: invalid petId format");
            return petUpdateEvent;
        }

        Pet existingPet;
        try {
            CompletableFuture<ObjectNode> petNodeFuture = entityService.getItem("Pet", Config.ENTITY_VERSION, petTechnicalId);
            ObjectNode petNode = petNodeFuture.get();
            if (petNode == null) {
                petUpdateEvent.setStatus("FAILED");
                logger.error("PetUpdateEvent processing failed: referenced Pet not found");
                return petUpdateEvent;
            }
            existingPet = entityService.getObjectMapper().treeToValue(petNode, Pet.class);
            existingPet.setId(petNode.get("technicalId").asText());
        } catch (Exception e) {
            petUpdateEvent.setStatus("FAILED");
            logger.error("PetUpdateEvent processing failed: error fetching Pet - {}", e.getMessage());
            return petUpdateEvent;
        }

        Pet updatedPet = new Pet();
        updatedPet.setId(null); // id will be assigned by entityService on addItem
        updatedPet.setName(existingPet.getName());
        updatedPet.setSpecies(existingPet.getSpecies());
        updatedPet.setBreed(existingPet.getBreed());
        updatedPet.setAge(existingPet.getAge());
        updatedPet.setStatus(existingPet.getStatus());

        Map<String, Object> updates = petUpdateEvent.getUpdatedFields();
        if (updates.containsKey("name")) {
            Object nameVal = updates.get("name");
            if (nameVal instanceof String && !((String) nameVal).isBlank()) {
                updatedPet.setName((String) nameVal);
            }
        }
        if (updates.containsKey("species")) {
            Object speciesVal = updates.get("species");
            if (speciesVal instanceof String && !((String) speciesVal).isBlank()) {
                updatedPet.setSpecies((String) speciesVal);
            }
        }
        if (updates.containsKey("breed")) {
            Object breedVal = updates.get("breed");
            if (breedVal instanceof String) {
                updatedPet.setBreed((String) breedVal);
            }
        }
        if (updates.containsKey("age")) {
            Object ageVal = updates.get("age");
            if (ageVal instanceof Number) {
                updatedPet.setAge(((Number) ageVal).intValue());
            }
        }
        if (updates.containsKey("status")) {
            Object statusVal = updates.get("status");
            if (statusVal instanceof String && !((String) statusVal).isBlank()) {
                updatedPet.setStatus((String) statusVal);
            }
        }

        try {
            CompletableFuture<UUID> addPetFuture = entityService.addItem("Pet", Config.ENTITY_VERSION, updatedPet);
            UUID newPetId = addPetFuture.get();
            updatedPet.setId(newPetId.toString());

            petUpdateEvent.setStatus("PROCESSED");
            logger.info("PetUpdateEvent {} processed successfully, created Pet version {}", petUpdateEvent.getId(), updatedPet.getId());
        } catch (Exception e) {
            petUpdateEvent.setStatus("FAILED");
            logger.error("PetUpdateEvent processing failed: error saving updated Pet - {}", e.getMessage());
        }

        return petUpdateEvent;
    }

}