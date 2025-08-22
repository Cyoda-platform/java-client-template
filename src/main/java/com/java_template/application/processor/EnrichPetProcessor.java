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

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
        if (entity == null) return false;
        // Enrichment runs after validation stage; ensure minimal required fields exist
        try {
            return entity.getName() != null && !entity.getName().trim().isEmpty()
                && entity.getSpecies() != null && !entity.getSpecies().trim().isEmpty();
        } catch (Exception e) {
            // If getters are not present or throw, consider invalid
            logger.warn("isValidEntity check failed due to exception: {}", e.getMessage());
            return false;
        }
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();
        if (entity == null) return null;

        logger.info("Enriching pet id={} name={} status={}", safeString(entity.getId()), safeString(entity.getName()), safeString(entity.getStatus()));

        // 1) Normalize breed (trim, capitalize words)
        try {
            String breed = entity.getBreed();
            if (breed != null) {
                String normalized = normalizeBreed(breed);
                entity.setBreed(normalized);
                logger.debug("Normalized breed to '{}'", normalized);
            }
        } catch (Exception e) {
            logger.warn("Failed to normalize breed for pet {}: {}", safeString(entity.getId()), e.getMessage());
        }

        // 2) Normalize and validate photos
        boolean photosOk = true;
        try {
            List<String> photos = entity.getPhotos();
            if (photos != null && !photos.isEmpty()) {
                // trim, dedupe, and validate URL format
                List<String> normalizedPhotos = photos.stream()
                    .filter(p -> p != null)
                    .map(String::trim)
                    .filter(p -> !p.isEmpty())
                    .collect(Collectors.toList());

                // dedupe while preserving order
                Set<String> dedup = new LinkedHashSet<>(normalizedPhotos);
                List<String> uniquePhotos = new ArrayList<>(dedup);

                // validate URL format for each photo
                List<String> invalids = new ArrayList<>();
                for (String urlStr : uniquePhotos) {
                    if (!isValidUrl(urlStr)) {
                        invalids.add(urlStr);
                    }
                }

                if (!invalids.isEmpty()) {
                    photosOk = false;
                    logger.warn("Pet {} has invalid photo URLs: {}", safeString(entity.getId()), invalids);
                } else {
                    entity.setPhotos(uniquePhotos);
                    logger.debug("Normalized photos list for pet {} to {} entries", safeString(entity.getId()), uniquePhotos.size());
                }
            } else {
                // No photos provided - depending on policy, this may be acceptable.
                logger.debug("Pet {} has no photos to validate", safeString(entity.getId()));
            }
        } catch (Exception e) {
            photosOk = false;
            logger.warn("Error validating photos for pet {}: {}", safeString(entity.getId()), e.getMessage());
        }

        // 3) Determine final status based on photo validation
        if (!photosOk) {
            // Policy chosen here: mark as validation_failed to force manual review
            try {
                entity.setStatus("validation_failed");
                logger.info("Pet {} marked as validation_failed due to photo issues", safeString(entity.getId()));
            } catch (Exception e) {
                logger.warn("Unable to set status on pet {}: {}", safeString(entity.getId()), e.getMessage());
            }
            // Optionally annotate error details in a bio or other field if present (skip to avoid inventing fields)
            return entity;
        }

        // 4) All enrichment passed -> set status to available (idempotent)
        try {
            String currentStatus = entity.getStatus();
            if (!"available".equalsIgnoreCase(currentStatus)) {
                entity.setStatus("available");
                logger.info("Pet {} status set to available", safeString(entity.getId()));
            } else {
                logger.debug("Pet {} already available; no status change", safeString(entity.getId()));
            }
        } catch (Exception e) {
            logger.warn("Unable to update status for pet {}: {}", safeString(entity.getId()), e.getMessage());
        }

        // Note: timestamps/audits are managed by the platform; do not modify other entities here.
        return entity;
    }

    // Helpers

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
            if (i < parts.length - 1) sb.append(' ');
        }
        return sb.toString();
    }

    private boolean isValidUrl(String urlStr) {
        if (urlStr == null || urlStr.trim().isEmpty()) return false;
        try {
            URL url = new URL(urlStr);
            String protocol = url.getProtocol();
            return "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol);
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private String safeString(Object o) {
        return o == null ? "<null>" : String.valueOf(o);
    }
}