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

import java.time.Instant;
import java.util.List;
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
            // 1. Normalize breed (trim and title-case)
            String breed = entity.getBreed();
            if (breed != null) {
                String normalized = normalizeBreed(breed);
                entity.setBreed(normalized);
            }

            // 2. Validate photos: if photos exist, ensure basic accessibility checks (valid URL scheme)
            List<String> photos = entity.getPhotos();
            boolean photosPresent = photos != null && !photos.isEmpty();
            if (photosPresent) {
                List<String> invalidPhotos = photos.stream()
                    .filter(p -> p == null || p.trim().isEmpty() || !(p.startsWith("http://") || p.startsWith("https://")))
                    .collect(Collectors.toList());

                if (!invalidPhotos.isEmpty()) {
                    // Policy chosen: mark as validation failed if any photo URL seems invalid/unreachable format
                    entity.setStatus("validation_failed");
                    logger.warn("Pet {} enrichment failed due to invalid photo URLs: {}", safeId(entity), invalidPhotos);
                    return entity;
                }
            }

            // 3. Derived fields or minor enrichment (e.g., ensure gender normalized)
            String gender = entity.getGender();
            if (gender != null) {
                String normalizedGender = gender.trim().toLowerCase();
                if ("male".equals(normalizedGender) || "female".equals(normalizedGender) || "unknown".equals(normalizedGender)) {
                    entity.setGender(normalizedGender);
                } else {
                    entity.setGender("unknown");
                }
            }

            // 4. On success, mark as available
            entity.setStatus("available");
            // Optionally update updatedAt if setter exists; keep defensive by checking nullability via try/catch
            try {
                entity.setUpdatedAt(Instant.now().toString());
            } catch (Exception ignore) {
                // If setter not present, ignore - persistence layer will handle timestamps
            }

            logger.info("Pet {} enriched successfully; status set to available", safeId(entity));
        } catch (Exception e) {
            // Resilient handling: mark validation failed and surface reason in logs
            try {
                entity.setStatus("validation_failed");
            } catch (Exception ignore) {
            }
            logger.error("Unexpected error while enriching pet {}: {}", safeId(entity), e.getMessage(), e);
        }

        return entity;
    }

    private String normalizeBreed(String breed) {
        if (breed == null) return null;
        String trimmed = breed.trim().toLowerCase();
        String[] parts = trimmed.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
            if (i < parts.length - 1) sb.append(" ");
        }
        return sb.toString();
    }

    private String safeId(Pet entity) {
        try {
            return entity.getId();
        } catch (Exception e) {
            return "<unknown-id>";
        }
    }
}