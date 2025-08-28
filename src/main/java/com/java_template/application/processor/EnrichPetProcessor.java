package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class EnrichPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichPetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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

        if (entity == null) {
            logger.warn("Received null Pet entity in EnrichPetProcessor");
            return null;
        }

        // Ensure collections are initialized (Pet.isValid ensures non-null, but be defensive)
        if (entity.getImages() == null) {
            entity.setImages(new ArrayList<>());
        }
        if (entity.getHealthRecords() == null) {
            entity.setHealthRecords(new ArrayList<>());
        }
        if (entity.getMetadata() == null) {
            entity.setMetadata(new java.util.HashMap<>());
        }

        Map<String, Object> metadata = entity.getMetadata();

        // 1) Add createdAt timestamp if missing
        if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
            String now = Instant.now().toString();
            entity.setCreatedAt(now);
            logger.debug("Set createdAt for pet {} to {}", entity.getPetId(), now);
        }

        // 2) Enrich images: add a default species-based image if none present
        if (entity.getImages().isEmpty()) {
            String species = (entity.getSpecies() != null && !entity.getSpecies().isBlank())
                    ? entity.getSpecies().toLowerCase().replaceAll("\\s+", "_")
                    : "pet";
            String defaultImage = "https://purrfect-pets.example/assets/images/" + species + ".png";
            entity.getImages().add(defaultImage);
            logger.info("Added default image for pet {}: {}", entity.getPetId(), defaultImage);
        }

        // 3) Add breed info into metadata (simple enrichment)
        if (entity.getBreed() != null && !entity.getBreed().isBlank()) {
            metadata.putIfAbsent("breedInfo", entity.getBreed());
            logger.debug("Added breedInfo to metadata for pet {}: {}", entity.getPetId(), entity.getBreed());
        }

        // 4) Compose tags: species, breed, age-group
        List<String> tags = new ArrayList<>();
        Object existingTags = metadata.get("tags");
        if (existingTags instanceof List<?>) {
            for (Object o : (List<?>) existingTags) {
                if (o != null) tags.add(o.toString());
            }
        }

        if (entity.getSpecies() != null && !entity.getSpecies().isBlank()) {
            String speciesTag = entity.getSpecies().trim();
            if (!tags.contains(speciesTag)) tags.add(speciesTag);
        }

        if (entity.getBreed() != null && !entity.getBreed().isBlank()) {
            String breedTag = entity.getBreed().trim();
            if (!tags.contains(breedTag)) tags.add(breedTag);
        }

        if (entity.getAge() != null) {
            String ageGroup = entity.getAge() < 2 ? "young" : (entity.getAge() <= 8 ? "adult" : "senior");
            if (!tags.contains(ageGroup)) tags.add(ageGroup);
        }

        metadata.put("tags", tags);
        logger.debug("Enrichment tags for pet {}: {}", entity.getPetId(), tags);

        // 5) Mark as enriched and record enrichment timestamp
        metadata.put("enriched", true);
        metadata.put("enrichedAt", Instant.now().toString());

        // 6) Transition status to Available if currently a persisted placeholder
        String status = entity.getStatus();
        if (status == null || status.isBlank() || "PERSISTED".equalsIgnoreCase(status)) {
            entity.setStatus("Available");
            logger.info("Pet {} status set to Available by enrichment", entity.getPetId());
        }

        // Additional small health heuristic: if healthRecords empty, note in metadata
        if (entity.getHealthRecords().isEmpty()) {
            metadata.putIfAbsent("healthNote", "No health records available; recommend vet check");
            logger.debug("Added healthNote for pet {}", entity.getPetId());
        }

        // Save updated metadata back to entity (already a reference, but ensure setter used)
        entity.setMetadata(metadata);

        return entity;
    }
}