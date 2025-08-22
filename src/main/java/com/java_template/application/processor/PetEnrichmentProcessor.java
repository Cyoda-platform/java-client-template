package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class PetEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetEnrichmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        // Normalize breed string
        String breed = entity.getBreed();
        if (breed != null) {
            String normalized = normalizeBreed(breed);
            entity.setBreed(normalized);
        }

        // Normalize species casing
        String species = entity.getSpecies();
        if (species != null) {
            entity.setSpecies(species.trim().toLowerCase(Locale.ROOT));
        }

        // Ensure status has a default if missing
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("available");
        }

        // Generate tags based on fields (merge with existing tags)
        List<String> tags = entity.getTags();
        if (tags == null) {
            tags = new ArrayList<>();
        }

        // Add species tag
        if (entity.getSpecies() != null && !entity.getSpecies().isBlank()) {
            String sp = entity.getSpecies().trim().toLowerCase(Locale.ROOT);
            if (!tags.contains(sp)) tags.add(sp);
        }

        // Add breed-based tag
        if (entity.getBreed() != null && !entity.getBreed().isBlank()) {
            String b = entity.getBreed().trim().toLowerCase(Locale.ROOT);
            if (!tags.contains(b)) tags.add(b);
        }

        // Add color tag
        if (entity.getColor() != null && !entity.getColor().isBlank()) {
            String colorTag = entity.getColor().trim().toLowerCase(Locale.ROOT);
            if (!tags.contains(colorTag)) tags.add(colorTag);
        }

        // Age-based tags
        Integer age = entity.getAge();
        if (age != null) {
            if (age <= 1 && !tags.contains("young")) tags.add("young");
            else if (age <= 7 && !tags.contains("adult")) tags.add("adult");
            else if (age > 7 && !tags.contains("senior")) tags.add("senior");
        }

        // Gender tag
        if (entity.getGender() != null && !entity.getGender().isBlank()) {
            String g = entity.getGender().trim().toLowerCase(Locale.ROOT);
            if (!tags.contains(g)) tags.add(g);
        }

        // Photo presence
        if (entity.getPhotos() != null && !entity.getPhotos().isEmpty()) {
            if (!tags.contains("has_photos")) tags.add("has_photos");
        }

        // Availability tag based on status
        if (entity.getStatus() != null) {
            String statusLower = entity.getStatus().trim().toLowerCase(Locale.ROOT);
            if ("available".equals(statusLower)) {
                if (!tags.contains("available")) tags.add("available");
            } else if ("pending".equals(statusLower)) {
                if (!tags.contains("pending")) tags.add("pending");
            } else if ("adopted".equals(statusLower)) {
                if (!tags.contains("adopted")) tags.add("adopted");
            }
        }

        // Deduplicate tags and set
        List<String> deduped = new ArrayList<>();
        for (String t : tags) {
            if (t != null) {
                String clean = t.trim().toLowerCase(Locale.ROOT);
                if (!clean.isBlank() && !deduped.contains(clean)) deduped.add(clean);
            }
        }
        entity.setTags(deduped);

        // Additional enrichment: ensure description is trimmed
        if (entity.getDescription() != null) {
            entity.setDescription(entity.getDescription().trim());
        }

        // If sourceMetadata present, ensure source is lowercase
        if (entity.getSourceMetadata() != null && entity.getSourceMetadata().getSource() != null) {
            entity.getSourceMetadata().setSource(entity.getSourceMetadata().getSource().trim().toLowerCase(Locale.ROOT));
        }

        logger.debug("Enriched pet id={} tags={} status={}", entity.getId(), entity.getTags(), entity.getStatus());
        return entity;
    }

    private String normalizeBreed(String breed) {
        if (breed == null) return null;
        String cleaned = breed.trim().replaceAll("\\s+", " ");
        // Basic normalization: title-case each word (e.g., "tabby mix" -> "Tabby Mix")
        StringBuilder sb = new StringBuilder();
        String[] parts = cleaned.split(" ");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            String lower = p.toLowerCase(Locale.ROOT);
            String capitalized = Character.toUpperCase(lower.charAt(0)) + (lower.length() > 1 ? lower.substring(1) : "");
            if (i > 0) sb.append(' ');
            sb.append(capitalized);
        }
        return sb.toString();
    }
}