package com.java_template.application.processor;
import com.java_template.application.entity.pete.version_1.Pet; //replace with actual entity name and version.
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
import java.util.Objects;

@Component
public class PersistPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistPetProcessor(SerializerFactory serializerFactory) {
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

        // Ensure importedAt is set to current time if missing
        if (entity.getImportedAt() == null || entity.getImportedAt().isBlank()) {
            entity.setImportedAt(Instant.now().toString());
            logger.debug("Set importedAt for pet {} to {}", entity.getId(), entity.getImportedAt());
        }

        // Ensure source is set
        if (entity.getSource() == null || entity.getSource().isBlank()) {
            entity.setSource("Petstore");
            logger.debug("Set default source for pet {} to Petstore", entity.getId());
        }

        // Ensure status has a sensible default
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("AVAILABLE");
            logger.debug("Set default status for pet {} to AVAILABLE", entity.getId());
        } else {
            // Normalize common status values
            String status = entity.getStatus().trim().toUpperCase();
            if ("PERSISTED".equals(status)) {
                entity.setStatus("AVAILABLE");
            } else {
                entity.setStatus(status);
            }
        }

        // Clean photos list: remove blank entries
        if (entity.getPhotos() != null) {
            List<String> cleaned = new ArrayList<>();
            for (String p : entity.getPhotos()) {
                if (p != null && !p.isBlank()) {
                    cleaned.add(p.trim());
                }
            }
            entity.setPhotos(cleaned);
        }

        // Ensure healthNotes present
        if (entity.getHealthNotes() == null || entity.getHealthNotes().isBlank()) {
            entity.setHealthNotes("No health notes provided");
            logger.debug("Set default healthNotes for pet {}", entity.getId());
        }

        // Ensure tags exist; infer from bio if missing
        if (entity.getTags() == null || entity.getTags().isEmpty()) {
            List<String> inferred = inferTagsFromBio(entity.getBio());
            entity.setTags(inferred);
            logger.debug("Inferred tags for pet {}: {}", entity.getId(), inferred);
        } else {
            // clean tags (trim, remove blanks)
            List<String> cleanedTags = new ArrayList<>();
            for (String t : entity.getTags()) {
                if (t != null && !t.isBlank()) {
                    cleanedTags.add(t.trim().toLowerCase());
                }
            }
            entity.setTags(cleanedTags);
        }

        // Ensure name trimmed
        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }

        // Basic enrichment: fill sex as 'unknown' if missing
        if (entity.getSex() == null || entity.getSex().isBlank()) {
            entity.setSex("unknown");
        }

        // Basic enrichment: size default if missing
        if (entity.getSize() == null || entity.getSize().isBlank()) {
            entity.setSize("medium");
        }

        // Nothing else should be added/removed for this entity; persistence is handled by Cyoda workflow

        return entity;
    }

    private List<String> inferTagsFromBio(String bio) {
        List<String> tags = new ArrayList<>();
        if (bio == null || bio.isBlank()) {
            tags.add("unknown");
            return tags;
        }
        String b = bio.toLowerCase();

        if (b.contains("playful") || b.contains("energetic") || b.contains("active")) {
            tags.add("playful");
        }
        if (b.contains("shy") || b.contains("timid")) {
            tags.add("shy");
        }
        if (b.contains("friendly") || b.contains("gentle") || b.contains("loving")) {
            tags.add("friendly");
        }
        if (b.contains("good with kids") || b.contains("kids") || b.contains("children")) {
            tags.add("good with kids");
        }
        if (b.contains("calm") || b.contains("quiet") || b.contains("laid back")) {
            tags.add("calm");
        }
        if (b.contains("trained") || b.contains("house-trained") || b.contains("house trained")) {
            tags.add("trained");
        }

        // Deduplicate and fallback
        List<String> unique = new ArrayList<>();
        for (String t : tags) {
            if (!unique.contains(t)) unique.add(t);
        }
        if (unique.isEmpty()) unique.add("unknown");
        return unique;
    }
}