package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistLaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistLaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistLaureateProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        String reqId = request != null ? request.getId() : "unknown";
        logger.info("Processing Laureate for request: {}", reqId);

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();

        try {
            // Build a simple search condition to find existing laureates by laureateId
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.laureateId", "EQUALS", getStringProperty(entity, "laureateId"))
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode results = itemsFuture.join();

            String laureateIdForLog = getStringProperty(entity, "laureateId");

            if (results != null && results.size() > 0) {
                // There is at least one existing record; perform basic deduplication based on rawPayload
                ObjectNode existing = (ObjectNode) results.get(0);
                String existingRaw = existing.has("rawPayload") && !existing.get("rawPayload").isNull()
                    ? existing.get("rawPayload").asText()
                    : null;
                String currentRaw = getStringProperty(entity, "rawPayload");

                if (existingRaw != null && existingRaw.equals(currentRaw)) {
                    // No change detected compared to stored record
                    String currentChangeType = getStringProperty(entity, "changeType");
                    if (currentChangeType == null) {
                        setProperty(entity, "changeType", "unchanged");
                    }
                    setProperty(entity, "published", Boolean.FALSE);
                    logger.info("Laureate {} unchanged - skipping publish", laureateIdForLog);
                } else {
                    // Content changed -> mark as updated and publish for notifications
                    setProperty(entity, "changeType", "updated");
                    setProperty(entity, "published", Boolean.TRUE);
                    logger.info("Laureate {} marked as updated - will be published", laureateIdForLog);
                }
            } else {
                // No existing record -> new laureate
                String currentChangeType = getStringProperty(entity, "changeType");
                if (currentChangeType == null) {
                    setProperty(entity, "changeType", "new");
                }
                setProperty(entity, "published", Boolean.TRUE);
                logger.info("Laureate {} is new - will be published", laureateIdForLog);
            }

            // Additional enrichment or side-effects may be added here in future.
            // IMPORTANT: Do not call updateItem on Laureate entity. The entity state changes above
            // will be persisted automatically by Cyoda based on the workflow.

        } catch (Exception ex) {
            String laureateIdForLog = getStringProperty(entity, "laureateId");
            logger.error("Error during PersistLaureateProcessor processing for laureateId={} : {}", laureateIdForLog, ex.getMessage(), ex);
            // On error, ensure we don't accidentally mark as published
            try {
                setProperty(entity, "published", Boolean.FALSE);
            } catch (Exception ignore) {
                // best-effort only
            }
            // Optionally mark changeType to indicate failure to persist/inspect
            String ct = getStringProperty(entity, "changeType");
            if (ct == null || ct.isBlank()) {
                setProperty(entity, "changeType", "error");
            }
        }

        return entity;
    }

    /* ---------- Reflection helpers to avoid direct compile-time member access ---------- */

    private String getStringProperty(Object obj, String propName) {
        Object val = getProperty(obj, propName);
        return val != null ? String.valueOf(val) : null;
    }

    private Object getProperty(Object obj, String propName) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();
        String capitalized = capitalize(propName);
        // Try getter
        String getterName = "get" + capitalized;
        try {
            Method m = findMethod(cls, getterName);
            if (m != null) {
                m.setAccessible(true);
                return m.invoke(obj);
            }
        } catch (Exception ignored) {
        }
        // Try boolean isX
        try {
            Method m = findMethod(cls, "is" + capitalized);
            if (m != null) {
                m.setAccessible(true);
                return m.invoke(obj);
            }
        } catch (Exception ignored) {
        }
        // Try field access
        try {
            Field f = findField(cls, propName);
            if (f != null) {
                f.setAccessible(true);
                return f.get(obj);
            }
        } catch (Exception ignored) {
        }
        // Fallback to ObjectMapper tree
        try {
            ObjectNode node = objectMapper.valueToTree(obj);
            if (node.has(propName) && !node.get(propName).isNull()) {
                return node.get(propName).asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean setProperty(Object obj, String propName, Object value) {
        if (obj == null) return false;
        Class<?> cls = obj.getClass();
        String capitalized = capitalize(propName);
        String setterName = "set" + capitalized;
        // Try setter method
        try {
            Method[] methods = cls.getMethods();
            for (Method m : methods) {
                if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    Class<?> paramType = m.getParameterTypes()[0];
                    Object coerced = coerceType(value, paramType);
                    m.invoke(obj, coerced);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        // Try direct field set
        try {
            Field f = findField(cls, propName);
            if (f != null) {
                f.setAccessible(true);
                Object coerced = coerceType(value, f.getType());
                f.set(obj, coerced);
                return true;
            }
        } catch (Exception ignored) {
        }
        // Last resort: no-op
        return false;
    }

    private Method findMethod(Class<?> cls, String name) {
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            for (Method m : cur.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    return m;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private Field findField(Class<?> cls, String name) {
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            try {
                Field f = cur.getDeclaredField(name);
                if (f != null) return f;
            } catch (NoSuchFieldException ignored) {
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private Object coerceType(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isAssignableFrom(value.getClass())) return value;
        // Primitive wrappers
        String sval = String.valueOf(value);
        try {
            if (targetType == String.class) return sval;
            if (targetType == Boolean.class || targetType == boolean.class) return Boolean.valueOf(sval);
            if (targetType == Integer.class || targetType == int.class) return Integer.valueOf(sval);
            if (targetType == Long.class || targetType == long.class) return Long.valueOf(sval);
            if (targetType == Double.class || targetType == double.class) return Double.valueOf(sval);
            if (targetType == Float.class || targetType == float.class) return Float.valueOf(sval);
        } catch (Exception ignored) {
        }
        // As a final attempt, try converting via ObjectMapper
        try {
            return objectMapper.convertValue(value, targetType);
        } catch (Exception ignored) {
        }
        return null;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.length() == 1) return s.toUpperCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}