package com.java_template.application.processor;

import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.application.entity.importaudit.version_1.ImportAudit;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CreateAuditProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateAuditProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateAuditProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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

    private boolean isValidEntity(HNItem entity) {
        return entity != null && entity.isValid();
    }

    private HNItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HNItem> context) {
        HNItem entity = context.entity();

        try {
            // Determine job reference: prefer processor request id, fallback to random UUID
            String jobRef = null;
            if (context.request() != null && context.request().getId() != null) {
                jobRef = context.request().getId();
            } else {
                jobRef = UUID.randomUUID().toString();
            }

            // Read properties from HNItem using reflection to avoid relying on generated accessors
            Long hnId = safeGetLongField(entity, "id");
            String status = safeGetStringField(entity, "status");
            String importTimestamp = safeGetStringField(entity, "importTimestamp");
            String originalJsonStr = safeGetStringField(entity, "originalJson");

            // Map HNItem status to audit outcome: STORED -> SUCCESS, otherwise FAILURE
            String outcome = "FAILURE";
            if (status != null && status.equalsIgnoreCase("STORED")) {
                outcome = "SUCCESS";
            }

            // Build audit as a JSON node to avoid depending on generated setters (Lombok)
            ObjectNode auditNode = objectMapper.createObjectNode();
            auditNode.put("auditId", UUID.randomUUID().toString());
            if (hnId != null) {
                auditNode.put("hnId", hnId);
            } else {
                auditNode.putNull("hnId");
            }
            auditNode.put("jobId", jobRef);
            auditNode.put("outcome", outcome);
            auditNode.put("timestamp", Instant.now().toString());

            // details: include status, importTimestamp and originalJson (try to parse originalJson if it's a JSON string)
            ObjectNode detailsNode = objectMapper.createObjectNode();
            if (status != null) detailsNode.put("status", status);
            if (importTimestamp != null) detailsNode.put("importTimestamp", importTimestamp);
            if (originalJsonStr != null) {
                try {
                    JsonNode parsed = objectMapper.readTree(originalJsonStr);
                    detailsNode.set("originalJson", parsed);
                } catch (Exception e) {
                    // If not valid JSON, store as plain string
                    detailsNode.put("originalJson", originalJsonStr);
                }
            }
            auditNode.set("details", detailsNode);

            // Persist ImportAudit using EntityService (add operation)
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                ImportAudit.ENTITY_NAME,
                String.valueOf(ImportAudit.ENTITY_VERSION),
                auditNode
            );
            // Wait for completion to ensure audit was recorded
            idFuture.join();
            logger.info("Created ImportAudit for HNItem id={} (audit recorded)", hnId);
        } catch (Exception ex) {
            logger.error("Failed to create ImportAudit for HNItem id={}: {}", entity != null ? trySafeId(entity) : null, ex.getMessage(), ex);
            // Do not modify the triggering entity via entityService update; keep entity unchanged here.
        }

        return entity;
    }

    // Reflection helpers
    private String safeGetStringField(Object obj, String fieldName) {
        Object val = safeGetField(obj, fieldName);
        return val != null ? String.valueOf(val) : null;
    }

    private Long safeGetLongField(Object obj, String fieldName) {
        Object val = safeGetField(obj, fieldName);
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            if (val != null) return Long.parseLong(String.valueOf(val));
        } catch (Exception ignored) {}
        return null;
    }

    private Object safeGetField(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            Field f = getFieldRecursive(obj.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable t) {
            logger.debug("Reflection access failed for field '{}' on {}: {}", fieldName, obj.getClass().getSimpleName(), t.getMessage());
            return null;
        }
    }

    private Field getFieldRecursive(Class<?> clazz, String fieldName) {
        Class<?> cur = clazz;
        while (cur != null) {
            try {
                return cur.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }

    private Object trySafeId(HNItem entity) {
        Long id = safeGetLongField(entity, "id");
        return id != null ? id : null;
    }
}