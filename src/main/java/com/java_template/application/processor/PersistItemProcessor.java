package com.java_template.application.processor;

import com.java_template.application.entity.hnitem.version_1.HNItem;
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

import java.lang.reflect.Field;

@Component
public class PersistItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(HNItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validate entity using reflection to avoid depending on Lombok-generated getters during compilation.
     */
    private boolean isValidEntity(HNItem entity) {
        if (entity == null) return false;
        try {
            Object idObj = getFieldValue(entity, "id");
            if (!(idObj instanceof Number) || ((Number) idObj).longValue() <= 0) return false;

            String type = safeGetString(entity, "type");
            if (type == null || type.isBlank()) return false;

            String originalJson = safeGetString(entity, "originalJson");
            if (originalJson == null || originalJson.isBlank()) return false;

            String importTimestamp = safeGetString(entity, "importTimestamp");
            if (importTimestamp == null || importTimestamp.isBlank()) return false;

            // status is allowed to be set by processors; ensure it's present but allow processor to set it if needed
            String status = safeGetString(entity, "status");
            if (status == null || status.isBlank()) {
                // not strictly invalid here; PersistItemProcessor will mark it appropriately.
                // But for safety, allow entities without status to pass validation stage for this processor.
                // Return true as other required fields are present.
            }

            return true;
        } catch (Exception ex) {
            logger.debug("Reflection-based validation failed: {}", ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Main business logic for persisting a HNItem.
     * Uses reflection to read/write fields to avoid direct dependency on generated accessor methods.
     */
    private HNItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HNItem> context) {
        HNItem entity = context.entity();

        if (entity == null) {
            logger.warn("Received null HNItem in PersistItemProcessor");
            return null;
        }

        Long hnId = null;
        try {
            Object idObj = getFieldValue(entity, "id");
            if (idObj instanceof Number) hnId = ((Number) idObj).longValue();
        } catch (Exception e) {
            // leave hnId null
        }

        String hnIdStr = hnId != null ? String.valueOf(hnId) : "unknown";

        try {
            // Ensure required persisted data exists before marking as STORED.
            String originalJson = safeGetString(entity, "originalJson");
            if (originalJson == null || originalJson.isBlank()) {
                logger.warn("HNItem {} missing originalJson, marking as FAILED", hnIdStr);
                setFieldValue(entity, "status", "FAILED");
                return entity;
            }

            String importTimestamp = safeGetString(entity, "importTimestamp");
            if (importTimestamp == null || importTimestamp.isBlank()) {
                logger.warn("HNItem {} missing importTimestamp, marking as FAILED", hnIdStr);
                setFieldValue(entity, "status", "FAILED");
                return entity;
            }

            // Business action: mark the HN item as persisted/stored.
            // Per rules: do not call entityService.update on the triggering entity.
            // Changing the entity state is sufficient; Cyoda will persist it.
            setFieldValue(entity, "status", "STORED");
            logger.info("HNItem {} marked as STORED", hnIdStr);
        } catch (Exception ex) {
            logger.error("Error while processing HNItem {}: {}", hnIdStr, ex.getMessage(), ex);
            try {
                setFieldValue(entity, "status", "FAILED");
            } catch (Exception ignore) {
            }
        }

        return entity;
    }

    // Helper: safely get string field value via reflection
    private String safeGetString(HNItem entity, String fieldName) {
        try {
            Object v = getFieldValue(entity, fieldName);
            return v == null ? null : v.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // Helper: get private field value using reflection
    private Object getFieldValue(HNItem entity, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field f = findField(entity.getClass(), fieldName);
        if (f == null) throw new NoSuchFieldException("Field " + fieldName + " not found on " + entity.getClass());
        f.setAccessible(true);
        return f.get(entity);
    }

    // Helper: set private field value using reflection
    private void setFieldValue(HNItem entity, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field f = findField(entity.getClass(), fieldName);
        if (f == null) throw new NoSuchFieldException("Field " + fieldName + " not found on " + entity.getClass());
        f.setAccessible(true);
        f.set(entity, value);
    }

    // Helper: walk class hierarchy to find declared field
    private Field findField(Class<?> cls, String fieldName) {
        Class<?> current = cls;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}