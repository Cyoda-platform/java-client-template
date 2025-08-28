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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class EnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validate the minimal required fields for enrichment.
     * Note: Do not rely on Pet.isValid() here because status may be populated by enrichment itself.
     */
    private boolean isValidEntity(Pet entity) {
        if (entity == null) return false;
        // id and name must be present
        String id = entity.getId();
        if (id == null || id.isBlank()) return false;
        String name = entity.getName();
        if (name == null || name.isBlank()) return false;
        // age must be non-null and non-negative
        Integer age = entity.getAge();
        if (age == null || age < 0) return false;
        // breed and description may be missing and will be normalized/enriched
        return true;
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        // Normalize breed: trim, collapse spaces, title case
        try {
            String breed = entity.getBreed();
            if (breed != null) {
                String normalized = collapseSpaces(breed).trim();
                if (!normalized.isBlank()) {
                    normalized = toTitleCase(normalized);
                    entity.setBreed(normalized);
                } else {
                    // keep as null if blank
                    entity.setBreed(null);
                }
            }
        } catch (Exception ex) {
            logger.warn("Failed to normalize breed for pet id {}: {}", entity.getId(), ex.getMessage());
        }

        // Normalize description: trim and collapse spaces; set default if missing
        try {
            String desc = entity.getDescription();
            if (desc == null || desc.isBlank()) {
                entity.setDescription("No description provided");
            } else {
                String normalizedDesc = collapseSpaces(desc).trim();
                entity.setDescription(normalizedDesc);
            }
        } catch (Exception ex) {
            logger.warn("Failed to normalize description for pet id {}: {}", entity.getId(), ex.getMessage());
        }

        // Compute age bucket and append to description (without adding new fields)
        try {
            Integer age = entity.getAge();
            if (age != null) {
                String ageBucket;
                if (age <= 0) {
                    ageBucket = "baby";
                } else if (age <= 2) {
                    ageBucket = "young";
                } else if (age <= 7) {
                    ageBucket = "adult";
                } else {
                    ageBucket = "senior";
                }
                // Append age bucket to description if not already present
                String desc = entity.getDescription() != null ? entity.getDescription() : "";
                String suffix = " Age group: " + ageBucket + ".";
                if (!desc.contains("Age group:")) {
                    entity.setDescription(desc + suffix);
                }
            }
        } catch (Exception ex) {
            logger.warn("Failed to compute age bucket for pet id {}: {}", entity.getId(), ex.getMessage());
        }

        // Determine initial status: if explicitly ADOPTED or PENDING_ADOPTION keep; otherwise set AVAILABLE
        try {
            String status = entity.getStatus();
            if (status == null || status.isBlank()) {
                entity.setStatus("AVAILABLE");
            } else {
                String s = status.trim().toUpperCase();
                if ("ADOPTED".equals(s) || "PENDING_ADOPTION".equals(s)) {
                    entity.setStatus(s);
                } else {
                    entity.setStatus("AVAILABLE");
                }
            }
        } catch (Exception ex) {
            logger.warn("Failed to determine status for pet id {}: {}", entity.getId(), ex.getMessage());
        }

        return entity;
    }

    // Helper: collapse multiple whitespace characters into single space
    private String collapseSpaces(String input) {
        if (input == null) return null;
        return input.replaceAll("\\s+", " ");
    }

    // Helper: simple title case - capitalize first letter of each word, lower-case the rest
    private String toTitleCase(String input) {
        if (input == null || input.isBlank()) return input;
        StringBuilder sb = new StringBuilder();
        String[] parts = input.split(" ");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            String first = p.substring(0, 1).toUpperCase();
            String rest = p.length() > 1 ? p.substring(1).toLowerCase() : "";
            if (i > 0) sb.append(" ");
            sb.append(first).append(rest);
        }
        return sb.toString();
    }
}