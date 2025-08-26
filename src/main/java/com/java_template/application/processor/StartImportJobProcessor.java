package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class StartImportJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartImportJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public StartImportJobProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            // Relax validation to allow minimal job objects (we only require non-null job object and itemJson)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        if (entity == null) return false;
        Object itemJson = getFieldValue(entity, "itemJson");
        return itemJson != null;
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        String jobRef = null;
        try {
            Object rawItem = getFieldValue(job, "itemJson");
            jobRef = safeGetStringField(job, "jobId");

            JsonNode itemNode = objectMapper.valueToTree(rawItem == null ? objectMapper.createObjectNode() : rawItem);

            Long hnId = null;
            if (itemNode.has("id") && !itemNode.get("id").isNull()) {
                if (itemNode.get("id").isNumber()) {
                    hnId = itemNode.get("id").asLong();
                } else {
                    try {
                        hnId = Long.valueOf(itemNode.get("id").asText());
                    } catch (NumberFormatException ignored) {
                        hnId = null;
                    }
                }
            }

            String type = null;
            if (itemNode.has("type") && !itemNode.get("type").isNull()) {
                type = itemNode.get("type").asText();
            }

            // Build HNItem entity to trigger HNItem workflow when persisted
            HNItem hnItem = new HNItem();

            // Use reflection to set fields to avoid relying on generated Lombok methods at compile-time
            if (hnId != null) setFieldValue(hnItem, "id", hnId);
            setFieldValue(hnItem, "type", type);
            // Persist the original JSON exactly as received
            setFieldValue(hnItem, "originalJson", itemNode.toString());
            // Initial status per workflow diagram
            setFieldValue(hnItem, "status", "CREATED");
            // importTimestamp will be enriched by HNItem workflow processor

            // Persist HNItem using EntityService (asynchronously)
            CompletableFuture<UUID> addFuture = entityService.addItem(
                HNItem.ENTITY_NAME,
                String.valueOf(HNItem.ENTITY_VERSION),
                hnItem
            );

            // Log result asynchronously; do not block workflow
            final String finalJobRef = jobRef;
            addFuture.whenComplete((technicalId, ex) -> {
                if (ex != null) {
                    logger.error("Failed to create HNItem for ImportJob {}, error: {}", finalJobRef == null ? "unknown" : finalJobRef, ex.getMessage(), ex);
                } else {
                    logger.info("Created HNItem (technicalId={}) for ImportJob {}", technicalId, finalJobRef == null ? "unknown" : finalJobRef);
                }
            });

            // Update job status to indicate processing has started.
            setFieldValue(job, "status", "PROCESSING");

        } catch (Exception e) {
            logger.error("Error while starting import for job {}: {}", jobRef == null ? "unknown" : jobRef, e.getMessage(), e);
            // mark job as failed; Cyoda will persist changes to this entity automatically
            if (job != null) {
                try {
                    setFieldValue(job, "status", "FAILED");
                } catch (Exception ex) {
                    logger.error("Unable to mark job FAILED via reflection: {}", ex.getMessage(), ex);
                }
            }
        }

        return job;
    }

    // Reflection helpers

    private Object getFieldValue(Object target, String fieldName) {
        if (target == null) return null;
        Field field = findField(target.getClass(), fieldName);
        if (field == null) return null;
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException e) {
            logger.debug("Unable to access field {} on {}: {}", fieldName, target.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private void setFieldValue(Object target, String fieldName, Object value) {
        if (target == null) return;
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            logger.debug("Field {} not found on {}", fieldName, target.getClass().getSimpleName());
            return;
        }
        try {
            field.setAccessible(true);
            // handle primitive long vs Long
            if (value != null) {
                Class<?> fieldType = field.getType();
                if ((fieldType == long.class || fieldType == Long.class) && value instanceof Number) {
                    field.set(target, ((Number) value).longValue());
                    return;
                }
                if (fieldType == String.class && !(value instanceof String)) {
                    field.set(target, String.valueOf(value));
                    return;
                }
            }
            field.set(target, value);
        } catch (IllegalAccessException e) {
            logger.debug("Unable to set field {} on {}: {}", fieldName, target.getClass().getSimpleName(), e.getMessage());
        }
    }

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

    private String safeGetStringField(Object target, String fieldName) {
        Object v = getFieldValue(target, fieldName);
        return v == null ? null : String.valueOf(v);
    }
}