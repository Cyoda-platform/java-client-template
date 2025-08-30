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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
        // Use structural validation provided by the entity.
        return entity != null && entity.isValid();
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product entity = context.entity();
        if (entity == null) {
            logger.warn("Product entity is null in execution context");
            return null;
        }

        try {
            // Convert entity to mutable ObjectNode to avoid compile-time reliance on Lombok-generated getters/setters.
            ObjectNode node = objectMapper.valueToTree(entity);

            // Normalize SKU (uppercase + trim)
            if (node.has("sku") && !node.get("sku").isNull()) {
                String sku = node.get("sku").asText("").trim();
                if (!sku.isEmpty()) {
                    node.put("sku", sku.toUpperCase());
                } else {
                    node.putNull("sku");
                }
            }

            // Trim name if present
            if (node.has("name") && !node.get("name").isNull()) {
                String name = node.get("name").asText("").trim();
                node.put("name", name);
            }

            // Ensure category present; default to "uncategorized" if missing or blank
            if (!node.has("category") || node.get("category").isNull() || node.get("category").asText("").isBlank()) {
                node.put("category", "uncategorized");
                logger.info("Product missing category. Defaulting to 'uncategorized' for sku={}", node.path("sku").asText(null));
            } else {
                node.put("category", node.get("category").asText("").trim());
            }

            // Ensure collections exist
            if (!node.has("media") || node.get("media").isNull() || !node.get("media").isArray()) {
                node.set("media", objectMapper.createArrayNode());
            }
            if (!node.has("variants") || node.get("variants").isNull() || !node.get("variants").isArray()) {
                node.set("variants", objectMapper.createArrayNode());
            }
            if (!node.has("bundles") || node.get("bundles").isNull() || !node.get("bundles").isArray()) {
                node.set("bundles", objectMapper.createArrayNode());
            }
            if (!node.has("events") || node.get("events").isNull() || !node.get("events").isArray()) {
                node.set("events", objectMapper.createArrayNode());
            }

            // Add a ProductCreated event if not present
            String currentSku = node.has("sku") && !node.get("sku").isNull() ? node.get("sku").asText() : null;
            boolean hasCreated = false;
            ArrayNode events = (ArrayNode) node.get("events");
            if (events != null) {
                for (JsonNode ev : events) {
                    if (ev != null && ev.has("type") && "ProductCreated".equals(ev.get("type").asText(null))) {
                        JsonNode payload = ev.get("payload");
                        if (payload != null && payload.has("sku") && currentSku != null) {
                            if (currentSku.equals(payload.get("sku").asText(null))) {
                                hasCreated = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (!hasCreated) {
                ObjectNode createdEvent = objectMapper.createObjectNode();
                createdEvent.put("type", "ProductCreated");
                createdEvent.put("at", Instant.now().toString());
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("sku", currentSku);
                createdEvent.set("payload", payload);
                events.add(createdEvent);
                logger.info("Added ProductCreated event for SKU={}", currentSku);
            }

            // Defensive clamps for numeric values
            if (node.has("price") && !node.get("price").isNull()) {
                double price = node.get("price").asDouble(0.0);
                if (price < 0.0) {
                    logger.warn("Product SKU={} had negative price {}. Clamping to 0.0", currentSku, price);
                    node.put("price", 0.0);
                }
            }
            if (node.has("quantityAvailable") && !node.get("quantityAvailable").isNull()) {
                int qty = node.get("quantityAvailable").asInt(0);
                if (qty < 0) {
                    logger.warn("Product SKU={} had negative quantityAvailable {}. Clamping to 0", currentSku, qty);
                    node.put("quantityAvailable", 0);
                }
            }

            // Convert back to Product instance
            Product updated = objectMapper.treeToValue(node, Product.class);
            return updated;
        } catch (Exception ex) {
            logger.error("Failed to process Product entity logic: {}", ex.getMessage(), ex);
            // In case of error, return original entity unchanged so workflow can decide
            return entity;
        }
    }
}