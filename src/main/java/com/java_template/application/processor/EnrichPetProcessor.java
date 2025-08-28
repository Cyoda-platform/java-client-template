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

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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

        // 1) Ensure tags - infer from bio/species if missing
        if (entity.getTags() == null || entity.getTags().isEmpty()) {
            List<String> inferred = inferTags(entity.getBio(), entity.getSpecies());
            entity.setTags(inferred);
            logger.info("Inferred tags for pet {}: {}", entity.getId(), inferred);
        } else {
            // Normalize tags: trim and remove blanks
            List<String> normalized = entity.getTags().stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim())
                .collect(Collectors.toList());
            entity.setTags(normalized);
        }

        // 2) Default healthNotes if missing
        if (entity.getHealthNotes() == null || entity.getHealthNotes().isBlank()) {
            entity.setHealthNotes("Health information not provided");
        }

        // 3) Normalize photos list: trim URLs and remove blank entries
        if (entity.getPhotos() != null) {
            List<String> cleaned = new ArrayList<>();
            for (String p : entity.getPhotos()) {
                if (p != null) {
                    String trimmed = p.trim();
                    if (!trimmed.isBlank()) {
                        cleaned.add(trimmed);
                    }
                }
            }
            entity.setPhotos(cleaned);
        }

        // 4) If status indicates a freshly persisted record, promote to ENRICHED
        if (entity.getStatus() == null || entity.getStatus().isBlank() || "PERSISTED".equalsIgnoreCase(entity.getStatus())) {
            entity.setStatus("ENRICHED");
        }

        // 5) Ensure importedAt is present (if missing, set to current ISO-8601 timestamp)
        if (entity.getImportedAt() == null || entity.getImportedAt().isBlank()) {
            // Use simple ISO instant string from java.time (avoid additional injections)
            try {
                String now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString();
                entity.setImportedAt(now);
            } catch (Exception ex) {
                // If anything goes wrong, log but do not fail processing
                logger.warn("Failed to set importedAt timestamp for pet {}: {}", entity.getId(), ex.getMessage());
            }
        }

        // All enrichment done - return modified entity. The workflow persistence will handle saving.
        return entity;
    }

    private List<String> inferTags(String bio, String species) {
        List<String> tags = new ArrayList<>();
        if (bio != null && !bio.isBlank()) {
            String lower = bio.toLowerCase(Locale.ROOT);
            if (lower.contains("playful") || lower.contains("play") ) tags.add("playful");
            if (lower.contains("shy")) tags.add("shy");
            if (lower.contains("friendly") || lower.contains("friendly with")) tags.add("friendly");
            if (lower.contains("energetic") || lower.contains("energies")) tags.add("energetic");
            if (lower.contains("gentle")) tags.add("gentle");
            if (lower.contains("cuddly") || lower.contains("cuddle")) tags.add("cuddly");
            if (lower.contains("quiet") || lower.contains("calm")) tags.add("quiet");
            if (lower.contains("good with kids") || lower.contains("kids")) tags.add("good with kids");
            if (lower.contains("house-trained") || lower.contains("house trained") || lower.contains("trained")) tags.add("house-trained");
            if (lower.contains("hypoallergenic")) tags.add("hypoallergenic");
            if (lower.contains("indoor")) tags.add("indoor");
        }

        // Always include species as a tag if available and not already present
        if (species != null && !species.isBlank()) {
            String s = species.trim().toLowerCase(Locale.ROOT);
            if (!s.isBlank() && !tags.contains(s)) {
                tags.add(s);
            }
        }

        // If still empty, add a fallback tag
        if (tags.isEmpty()) {
            tags.add("unknown");
        }

        return tags;
    }
}