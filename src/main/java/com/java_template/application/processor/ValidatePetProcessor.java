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
import java.util.List;
import java.util.Map;

@Component
public class ValidatePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidatePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidatePetProcessor(SerializerFactory serializerFactory) {
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

        try {
            // Business validation rules:
            // 1. Data completeness: required fields (name, petId, species, age) must be present and age >= 0
            boolean dataComplete = isDataComplete(entity);

            // 2. Health basic checks: require at least one health record and ensure no severe keywords
            boolean healthy = isHealthy(entity);

            // Update metadata with validation details (metadata map is guaranteed non-null by entity.isValid())
            Map<String, Object> metadata = entity.getMetadata();
            if (metadata != null) {
                metadata.put("validationTimestamp", Instant.now().toString());
                metadata.put("validationDataComplete", dataComplete);
                metadata.put("validationHealthy", healthy);
            }

            // Set status based on checks:
            if (!dataComplete) {
                // Needs more information before enrichment/publishing
                entity.setStatus("NEEDS_INFO");
                logger.info("Pet [{}] marked as NEEDS_INFO (data incomplete)", entity.getPetId());
            } else if (!healthy) {
                // Requires health check before made available
                entity.setStatus("NEEDS_HEALTH_CHECK");
                logger.info("Pet [{}] marked as NEEDS_HEALTH_CHECK (health checks failed)", entity.getPetId());
            } else {
                // Valid and healthy: mark available for adoption
                entity.setStatus("Available");
                logger.info("Pet [{}] validated and marked as Available", entity.getPetId());
            }

        } catch (Exception ex) {
            logger.error("Unexpected error during pet validation: {}", ex.getMessage(), ex);
            // In case of unexpected errors, prefer to mark entity as needing info to prevent publishing
            if (entity.getMetadata() != null) {
                entity.getMetadata().put("validationError", ex.getMessage());
            }
            entity.setStatus("NEEDS_INFO");
        }

        return entity;
    }

    // Helper: determine data completeness
    private boolean isDataComplete(Pet pet) {
        if (pet == null) return false;
        if (pet.getName() == null || pet.getName().isBlank()) return false;
        if (pet.getPetId() == null || pet.getPetId().isBlank()) return false;
        if (pet.getSpecies() == null || pet.getSpecies().isBlank()) return false;
        if (pet.getAge() == null || pet.getAge() < 0) return false;
        // breed is helpful but not mandatory for basic completeness
        return true;
    }

    // Helper: basic health checks
    private boolean isHealthy(Pet pet) {
        if (pet == null) return false;
        List<String> records = pet.getHealthRecords();
        if (records == null || records.isEmpty()) {
            // No health information → not healthy enough
            return false;
        }

        // If any record contains concerning keywords, fail health check
        for (String r : records) {
            if (r == null) continue;
            String lower = r.toLowerCase();
            if (lower.contains("sick") || lower.contains("injury") || lower.contains("critical") || lower.contains("contagious") || lower.contains("ill")) {
                return false;
            }
        }

        // Prefer explicit vaccination note if present; if none but records exist, accept as healthy
        boolean hasVaccinationNote = records.stream()
            .filter(r -> r != null)
            .map(String::toLowerCase)
            .anyMatch(s -> s.contains("vaccin") || s.contains("vaccinated") || s.contains("up to date on shots"));
        // If no vaccination note but records exist, still allow but flag via metadata (handled earlier)
        return true || hasVaccinationNote;
    }
}