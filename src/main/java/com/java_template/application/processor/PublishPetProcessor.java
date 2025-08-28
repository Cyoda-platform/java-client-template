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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Component
public class PublishPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PublishPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PublishPetProcessor(SerializerFactory serializerFactory,
                               EntityService entityService,
                               ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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
            logger.warn("Received null Pet entity in processing context");
            return null;
        }

        logger.info("PublishPetProcessor executing business logic for pet id: {}, current status: {}",
                safeGetId(entity), safeGetStatus(entity));

        // 1. Ensure basic defaults for optional fields so Pet is in a consistent state
        String healthNotes = getStringField(entity, "healthNotes");
        if (healthNotes == null || healthNotes.isBlank()) {
            setStringField(entity, "healthNotes", "Not specified");
            logger.debug("Set default healthNotes for pet id {}", safeGetId(entity));
        }

        if (entity.getSize() == null || entity.getSize().isBlank()) {
            entity.setSize("unknown");
            logger.debug("Set default size for pet id {}", safeGetId(entity));
        }

        // 2. Ensure tags list exists
        List<String> tags = entity.getTags();
        if (tags == null) {
            tags = new ArrayList<>();
            entity.setTags(tags);
        }

        // 3. Validate image presence / quality simple heuristic:
        boolean photosOk = false;
        List<String> photos = entity.getPhotos();
        if (photos != null && !photos.isEmpty()) {
            photosOk = true;
            for (String p : photos) {
                if (p == null || p.isBlank()) {
                    photosOk = false;
                    break;
                }
            }
        }

        // If no photos or invalid photos, tag the pet to indicate images needed
        if (!photosOk) {
            if (!tags.contains("needs_images")) {
                tags.add("needs_images");
            }
            logger.info("Pet id {} missing valid photos; tagging with 'needs_images' and not publishing as AVAILABLE", safeGetId(entity));
        }

        // 4. Only transition to AVAILABLE when the pet has completed image processing stage
        String currentStatus = safeGetStatus(entity);
        if (currentStatus != null && currentStatus.equalsIgnoreCase("IMAGES_READY") && photosOk) {
            setStringField(entity, "status", "AVAILABLE");
            logger.info("Pet id {} transitioned from IMAGES_READY to AVAILABLE", safeGetId(entity));
        } else {
            // If already marked available by admin or other flows, leave as-is.
            if (currentStatus == null || currentStatus.isBlank()) {
                // If status missing but photos are present, set to AVAILABLE (safe fallback)
                if (photosOk) {
                    setStringField(entity, "status", "AVAILABLE");
                    logger.info("Pet id {} had empty status but has photos; setting status to AVAILABLE", safeGetId(entity));
                } else {
                    // Keep status as-is (or mark as ENRICHED if that's appropriate)
                    // no-op: nothing to do when no valid status and no photos
                    logger.debug("Pet id {} status unchanged (no valid status and no photos)", safeGetId(entity));
                }
            } else {
                logger.debug("Pet id {} status not eligible for publish transition: {}", safeGetId(entity), currentStatus);
            }
        }

        // 5. Ensure importedAt present: if missing, set to now (ISO-8601)
        if (entity.getImportedAt() == null || entity.getImportedAt().isBlank()) {
            String now = Instant.now().toString();
            entity.setImportedAt(now);
            logger.debug("Set importedAt for pet id {} to {}", safeGetId(entity), now);
        }

        // 6. Final touch: remove duplicate/blank tags
        List<String> cleanedTags = new ArrayList<>();
        for (String t : entity.getTags()) {
            if (t != null) {
                String trimmed = t.trim();
                if (!trimmed.isBlank() && !cleanedTags.contains(trimmed)) {
                    cleanedTags.add(trimmed);
                }
            }
        }
        entity.setTags(cleanedTags);

        // The processor must not perform add/update/delete on the triggering entity via EntityService.
        // The changed entity will be persisted by the Cyoda workflow automatically.
        return entity;
    }

    // Reflection helpers to handle fields/getters/setters that may not be present as direct methods.
    private String getStringField(Pet entity, String fieldName) {
        if (entity == null || fieldName == null) {
            return null;
        }
        // Try getter first
        String cap = capitalize(fieldName);
        try {
            Method getter = entity.getClass().getMethod("get" + cap);
            Object val = getter.invoke(entity);
            return val == null ? null : String.valueOf(val);
        } catch (Exception ignored) {
            // fallback to direct field access
        }
        try {
            Field f = entity.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object val = f.get(entity);
            return val == null ? null : String.valueOf(val);
        } catch (Exception e) {
            logger.debug("Unable to read field '{}' on Pet: {}", fieldName, e.getMessage());
            return null;
        }
    }

    private void setStringField(Pet entity, String fieldName, String value) {
        if (entity == null || fieldName == null) {
            return;
        }
        String cap = capitalize(fieldName);
        try {
            Method setter = entity.getClass().getMethod("set" + cap, String.class);
            setter.invoke(entity, value);
            return;
        } catch (Exception ignored) {
            // fallback to direct field access
        }
        try {
            Field f = entity.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(entity, value);
        } catch (Exception e) {
            logger.debug("Unable to set field '{}' on Pet: {}", fieldName, e.getMessage());
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }

    // Safe getters to avoid compile-time dependency on specific Pet getters for id/status
    private String safeGetId(Pet entity) {
        String id = getStringField(entity, "id");
        if (id != null) return id;
        try {
            Method m = entity.getClass().getMethod("getId");
            Object val = m.invoke(entity);
            return val == null ? null : String.valueOf(val);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeGetStatus(Pet entity) {
        // Prefer existing getStatus if available
        try {
            Method m = entity.getClass().getMethod("getStatus");
            Object val = m.invoke(entity);
            return val == null ? null : String.valueOf(val);
        } catch (Exception ignored) {
        }
        return getStringField(entity, "status");
    }
}