package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ErrorInfo;
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

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
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
            // 1. Data completeness: required fields (name, petId, species, age) must be present and age >= 0
            boolean dataComplete = isDataComplete(entity);

            // 2. Health basic checks: require at least one health record and ensure no severe keywords
            boolean healthy = isHealthy(entity);

            // Update metadata with validation details
            Map<String, Object> metadata = getOrCreateMetadata(entity);
            metadata.put("validationTimestamp", Instant.now().toString());
            metadata.put("validationDataComplete", dataComplete);
            metadata.put("validationHealthy", healthy);

            // Persist metadata back
            setFieldValue(entity, "metadata", metadata);

            // Determine identifier for logging (petId may be null)
            String petBusinessId = safeGetString(entity, "petId");

            // Set status based on checks:
            if (!dataComplete) {
                // Needs more information before enrichment/publishing
                setFieldValue(entity, "status", "NEEDS_INFO");
                logger.info("Pet [{}] marked as NEEDS_INFO (data incomplete)", petBusinessId);
            } else if (!healthy) {
                // Requires health check before made available
                setFieldValue(entity, "status", "NEEDS_HEALTH_CHECK");
                logger.info("Pet [{}] marked as NEEDS_HEALTH_CHECK (health checks failed)", petBusinessId);
            } else {
                // Valid and healthy: mark available for adoption
                setFieldValue(entity, "status", "Available");
                logger.info("Pet [{}] validated and marked as Available", petBusinessId);
            }

        } catch (Exception ex) {
            logger.error("Unexpected error during pet validation: {}", ex.getMessage(), ex);
            try {
                Map<String, Object> metadata = getOrCreateMetadata(entity);
                metadata.put("validationError", ex.getMessage());
                setFieldValue(entity, "metadata", metadata);
            } catch (Exception ignore) {
                // Best-effort only
            }
            try {
                setFieldValue(entity, "status", "NEEDS_INFO");
            } catch (Exception ignore) {
            }
        }

        return entity;
    }

    // Helper: determine data completeness using reflection (avoid relying on Lombok-generated accessors)
    private boolean isDataComplete(Pet pet) {
        if (pet == null) return false;
        String name = safeGetString(pet, "name");
        String petId = safeGetString(pet, "petId");
        String species = safeGetString(pet, "species");
        Integer age = safeGetInteger(pet, "age");

        if (name == null || name.isBlank()) return false;
        if (petId == null || petId.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (age == null || age < 0) return false;

        // breed is helpful but not mandatory
        return true;
    }

    // Helper: basic health checks using reflection
    private boolean isHealthy(Pet pet) {
        if (pet == null) return false;
        List<String> records = safeGetList(pet, "healthRecords");
        if (records == null || records.isEmpty()) {
            // No health information → not healthy enough
            return false;
        }

        // If any record contains concerning keywords, fail health check
        for (String r : records) {
            if (r == null) continue;
            String lower = r.toLowerCase();
            if (lower.contains("sick") || lower.contains("injury") || lower.contains("critical") || lower.contains("contagious") || lower.contains("ill") || lower.contains("untreated")) {
                return false;
            }
        }

        // Accept as healthy if no concerning keywords found.
        return true;
    }

    // Reflection helpers

    private Map<String, Object> getOrCreateMetadata(Pet pet) {
        try {
            Object metaObj = getFieldValue(pet, "metadata");
            if (metaObj == null) {
                Map<String, Object> meta = new HashMap<>();
                setFieldValue(pet, "metadata", meta);
                return meta;
            }
            if (metaObj instanceof Map) {
                //noinspection unchecked
                return (Map<String, Object>) metaObj;
            } else {
                Map<String, Object> meta = new HashMap<>();
                meta.put("rawMetadata", metaObj);
                setFieldValue(pet, "metadata", meta);
                return meta;
            }
        } catch (Exception e) {
            // fallback
            return new HashMap<>();
        }
    }

    private Object getFieldValue(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (NoSuchFieldException nsf) {
            // try superclass (unlikely) or return null
            Class<?> cls = obj.getClass().getSuperclass();
            while (cls != null) {
                try {
                    Field f = cls.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f.get(obj);
                } catch (Exception ignored) {
                }
                cls = cls.getSuperclass();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void setFieldValue(Object obj, String fieldName, Object value) {
        if (obj == null) return;
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (NoSuchFieldException nsf) {
            // try superclasses
            Class<?> cls = obj.getClass().getSuperclass();
            while (cls != null) {
                try {
                    Field f = cls.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    f.set(obj, value);
                    return;
                } catch (Exception ignored) {
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception e) {
            logger.debug("Failed to set field {} on {}: {}", fieldName, obj != null ? obj.getClass().getSimpleName() : "null", e.getMessage());
        }
    }

    private String safeGetString(Object obj, String fieldName) {
        Object v = getFieldValue(obj, fieldName);
        if (v == null) return null;
        return v.toString();
    }

    private Integer safeGetInteger(Object obj, String fieldName) {
        Object v = getFieldValue(obj, fieldName);
        if (v == null) return null;
        if (v instanceof Integer) return (Integer) v;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.valueOf(v.toString());
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> safeGetList(Object obj, String fieldName) {
        Object v = getFieldValue(obj, fieldName);
        if (v == null) return null;
        if (v instanceof List) return (List<String>) v;
        return null;
    }
}