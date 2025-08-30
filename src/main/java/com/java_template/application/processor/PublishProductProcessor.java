package com.java_template.application.processor;
import com.java_template.application.entity.product.version_1.Product;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PublishProductProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PublishProductProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PublishProductProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Product.class)
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

    private boolean isValidEntity(Product entity) {
        // Basic structural validation + ensure category present (business rule)
        if (entity == null) return false;
        if (!entity.isValid()) return false;
        if (entity.getCategory() == null || entity.getCategory().isBlank()) return false;
        return true;
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product entity = context.entity();
        if (entity == null) {
            logger.warn("Product entity is null in execution context");
            return null;
        }

        // Normalize SKU and name
        if (entity.getSku() != null) {
            String sku = entity.getSku().trim();
            if (!sku.isEmpty()) {
                entity.setSku(sku.toUpperCase());
            }
        }

        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }

        // Ensure category is set (business requirement: Product must include category).
        if (entity.getCategory() == null || entity.getCategory().isBlank()) {
            // Default to "uncategorized" if missing to ensure downstream systems always have a category.
            entity.setCategory("uncategorized");
            logger.info("Product SKU={} missing category. Defaulting to 'uncategorized'", entity.getSku());
        } else {
            entity.setCategory(entity.getCategory().trim());
        }

        // Ensure media/variants/bundles/events lists are non-null to avoid NPEs in downstream consumers
        if (entity.getMedia() == null) {
            entity.setMedia(new ArrayList<>());
        }
        if (entity.getVariants() == null) {
            entity.setVariants(new ArrayList<>());
        }
        if (entity.getBundles() == null) {
            entity.setBundles(new ArrayList<>());
        }
        if (entity.getEvents() == null) {
            entity.setEvents(new ArrayList<>());
        }

        // Add a ProductCreated event if not already present.
        // We do a simple check: if events contains any map with type "ProductCreated" and payload sku equals this sku, skip.
        boolean hasCreated = false;
        if (entity.getEvents() != null) {
            for (Object ev : entity.getEvents()) {
                if (ev instanceof Map) {
                    try {
                        Map<?, ?> m = (Map<?, ?>) ev;
                        Object type = m.get("type");
                        Object payload = m.get("payload");
                        if ("ProductCreated".equals(type) && payload instanceof Map) {
                            Object skuInPayload = ((Map<?, ?>) payload).get("sku");
                            if (skuInPayload != null && skuInPayload.equals(entity.getSku())) {
                                hasCreated = true;
                                break;
                            }
                        }
                    } catch (Exception ex) {
                        // ignore and continue
                    }
                }
            }
        }

        if (!hasCreated) {
            Map<String, Object> createdEvent = new HashMap<>();
            createdEvent.put("type", "ProductCreated");
            createdEvent.put("at", Instant.now().toString());
            Map<String, Object> payload = new HashMap<>();
            payload.put("sku", entity.getSku());
            createdEvent.put("payload", payload);
            entity.getEvents().add(createdEvent);
            logger.info("Added ProductCreated event for SKU={}", entity.getSku());
        }

        // Ensure price and quantityAvailable are non-negative (defensive)
        if (entity.getPrice() != null && entity.getPrice() < 0.0) {
            logger.warn("Product SKU={} had negative price {}. Clamping to 0.0", entity.getSku(), entity.getPrice());
            entity.setPrice(0.0);
        }
        if (entity.getQuantityAvailable() != null && entity.getQuantityAvailable() < 0) {
            logger.warn("Product SKU={} had negative quantityAvailable {}. Clamping to 0", entity.getSku(), entity.getQuantityAvailable());
            entity.setQuantityAvailable(0);
        }

        // Business note: We do not perform persistence operations on the triggering entity here.
        // Cyoda will persist the modified entity state automatically by workflow. Any additional entities
        // (e.g., catalogs, audit logs) should be created via entityService if required.

        return entity;
    }
}