package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
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
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Field;

@Component
public class SuspendUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SuspendUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SuspendUserProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(User.class)
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

    private boolean isValidEntity(User entity) {
        return entity != null && entity.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User entity = context.entity();
        if (entity == null) {
            logger.warn("SuspendUserProcessor received null entity in execution context");
            return null;
        }

        // Use reflection to read/write fields to avoid depending on generated accessor methods at compile-time
        String currentStatus = getStringField(entity, "status");
        String userId = getStringField(entity, "userId");
        if (currentStatus == null) currentStatus = "";

        // Only allow suspend if user is in Active or Trusted state (per workflow)
        if (currentStatus.equalsIgnoreCase("Suspended")) {
            logger.info("User '{}' is already suspended. No action taken.", userId != null ? userId : "unknown");
            return entity;
        }

        if (!(currentStatus.equalsIgnoreCase("Active") || currentStatus.equalsIgnoreCase("Trusted"))) {
            logger.info("User '{}' has status '{}' and cannot be suspended by this processor. No action taken.", userId != null ? userId : "unknown", currentStatus);
            return entity;
        }

        // Perform suspension: set status and record suspension timestamp in preferences map
        setStringField(entity, "status", "Suspended");

        Map<String, Object> prefs = getMapField(entity, "preferences");
        if (prefs == null) {
            prefs = new HashMap<>();
            setMapField(entity, "preferences", prefs);
        }

        prefs.put("suspendedAt", Instant.now().toString());
        prefs.put("suspensionMethod", "manual");

        logger.info("User '{}' suspended successfully.", userId != null ? userId : "unknown");

        // Note: Do not call entityService to update this same entity. Cyoda will persist changes to the triggering entity automatically.
        // entityService can be used to record related artifacts if needed (not used here).

        return entity;
    }

    // Reflection helpers to access private fields on the User entity without relying on generated accessors.
    private String getStringField(User entity, String fieldName) {
        try {
            Field f = findField(entity, fieldName);
            if (f == null) return null;
            Object v = f.get(entity);
            return v != null ? v.toString() : null;
        } catch (Exception e) {
            logger.debug("Unable to read string field '{}' via reflection: {}", fieldName, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapField(User entity, String fieldName) {
        try {
            Field f = findField(entity, fieldName);
            if (f == null) return null;
            Object v = f.get(entity);
            if (v instanceof Map) return (Map<String, Object>) v;
            return null;
        } catch (Exception e) {
            logger.debug("Unable to read map field '{}' via reflection: {}", fieldName, e.getMessage());
            return null;
        }
    }

    private void setStringField(User entity, String fieldName, String value) {
        try {
            Field f = findField(entity, fieldName);
            if (f == null) {
                logger.debug("Field '{}' not found on entity", fieldName);
                return;
            }
            f.set(entity, value);
        } catch (Exception e) {
            logger.warn("Failed to set field '{}' via reflection: {}", fieldName, e.getMessage(), e);
        }
    }

    private void setMapField(User entity, String fieldName, Map<String, Object> map) {
        try {
            Field f = findField(entity, fieldName);
            if (f == null) {
                logger.debug("Field '{}' not found on entity", fieldName);
                return;
            }
            f.set(entity, map);
        } catch (Exception e) {
            logger.warn("Failed to set map field '{}' via reflection: {}", fieldName, e.getMessage(), e);
        }
    }

    private Field findField(User entity, String fieldName) {
        Class<?> cls = entity.getClass();
        while (cls != null) {
            try {
                Field f = cls.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            }
        }
        return null;
    }
}