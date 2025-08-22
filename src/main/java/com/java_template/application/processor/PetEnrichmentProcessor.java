package com.java_template.application.processor;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Pet.class)
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

        try {
            // Normalize breed to a canonical form
            String breed = entity.getBreed();
            if (breed != null && !breed.isBlank()) {
                String normalized = normalizeBreed(breed);
                entity.setBreed(normalized);
            }

            // Normalize source metadata source if present
            if (entity.getSourceMetadata() != null) {
                Pet.SourceMetadata sm = entity.getSourceMetadata();
                if (sm.getSource() != null && !sm.getSource().isBlank()) {
                    sm.setSource(sm.getSource().trim().toLowerCase());
                }
                // externalId trim
                if (sm.getExternalId() != null && !sm.getExternalId().isBlank()) {
                    sm.setExternalId(sm.getExternalId().trim());
                }
            }

            // Ensure status has a reasonable default if missing
            if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                entity.setStatus("available");
            }

            // Generate tags based on available attributes
            List<String> tags = generateTags(entity);
            entity.setTags(tags);

            logger.debug("Enriched pet id={} name={} breed={} tags={}", entity.getId(), entity.getName(), entity.getBreed(), entity.getTags());
        } catch (Exception ex) {
            logger.error("Error during Pet enrichment for id={}: {}", entity != null ? entity.getId() : "null", ex.getMessage(), ex);
        }

        return entity;
    }

    // Simple normalization with common mappings and capitalization
    private String normalizeBreed(String rawBreed) {
        if (rawBreed == null) return null;
        String b = rawBreed.trim();
        if (b.isBlank()) return b;

        String lower = b.toLowerCase();

        // common mappings
        if (lower.contains("domestic short") || lower.contains("dsh") || lower.contains("domestic shorthair")) {
            return "Domestic Shorthair";
        }
        if (lower.contains("domestic long") || lower.contains("dlh") || lower.contains("domestic longhair")) {
            return "Domestic Longhair";
        }
        if (lower.contains("tabby")) {
            return "Tabby";
        }
        if (lower.contains("labrador") || lower.contains("lab")) {
            return "Labrador Retriever";
        }
        if (lower.contains("german shepherd") || lower.contains("german shepherd dog")) {
            return "German Shepherd";
        }
        // Fallback: capitalize each word
        String[] parts = b.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isBlank()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1).toLowerCase());
            if (i < parts.length - 1) sb.append(' ');
        }
        return sb.toString();
    }

    private List<String> generateTags(Pet pet) {
        Set<String> tagSet = new HashSet<>();

        // species
        if (pet.getSpecies() != null && !pet.getSpecies().isBlank()) {
            tagSet.add(pet.getSpecies().trim().toLowerCase());
        }

        // gender
        if (pet.getGender() != null && !pet.getGender().isBlank()) {
            tagSet.add(pet.getGender().trim().toLowerCase());
        }

        // age-based tags (simple heuristics)
        Integer age = pet.getAge();
        if (age != null) {
            if (age < 1) {
                tagSet.add("baby");
            } else if (age < 3) {
                tagSet.add("young");
            } else if (age >= 7) {
                tagSet.add("senior");
            }
        }

        // color (split common separators)
        if (pet.getColor() != null && !pet.getColor().isBlank()) {
            String[] colors = pet.getColor().split("[,;/]");
            for (String c : colors) {
                String cc = c.trim().toLowerCase();
                if (!cc.isBlank()) tagSet.add(cc);
            }
        }

        // breed keywords
        if (pet.getBreed() != null && !pet.getBreed().isBlank()) {
            String[] parts = pet.getBreed().split("\\s+");
            for (String p : parts) {
                String pp = p.trim().toLowerCase();
                if (pp.length() > 2) { // avoid noise
                    tagSet.add(pp);
                }
            }
        }

        // location tag
        if (pet.getLocation() != null && !pet.getLocation().isBlank()) {
            tagSet.add(pet.getLocation().trim().toLowerCase());
        }

        // include a default availability tag based on status
        if (pet.getStatus() != null && !pet.getStatus().isBlank()) {
            tagSet.add(pet.getStatus().trim().toLowerCase());
        }

        // Preserve any existing tags that are meaningful
        if (pet.getTags() != null) {
            for (String t : pet.getTags()) {
                if (t != null && !t.isBlank()) tagSet.add(t.trim().toLowerCase());
            }
        }

        return new ArrayList<>(tagSet);
    }
}