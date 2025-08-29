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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ValidateProductProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateProductProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    @Autowired
    public ValidateProductProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
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
        return entity != null && entity.isValid();
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product entity = context.entity();

        try {
            // Log incoming product state
            logger.debug("ValidateProductProcessor - starting for productId={}, name={}, category={}, price={}, metadata={}",
                    entity.getProductId(), entity.getName(), entity.getCategory(), entity.getPrice(), entity.getMetadata());

            boolean changed = false;

            // 1) Normalize price: round to 2 decimals if present
            Double price = entity.getPrice();
            if (price != null) {
                double normalized = Math.round(price * 100.0) / 100.0;
                if (Double.isNaN(normalized) || Double.isInfinite(normalized)) {
                    normalized = 0.0;
                }
                if (!normalizedEquals(normalized, price)) {
                    entity.setPrice(normalized);
                    changed = true;
                    logger.debug("Normalized price from {} to {}", price, normalized);
                }
            }

            // 2) Enrich category from metadata if missing
            String category = entity.getCategory();
            if (category == null || category.isBlank()) {
                String metadata = entity.getMetadata();
                if (metadata != null && !metadata.isBlank()) {
                    try {
                        JsonNode metaNode = objectMapper.readTree(metadata);
                        if (metaNode.has("category") && !metaNode.get("category").asText().isBlank()) {
                            String inferred = metaNode.get("category").asText();
                            entity.setCategory(inferred);
                            changed = true;
                            logger.debug("Inferred category from metadata: {}", inferred);
                        } else if (metaNode.has("tags") && metaNode.get("tags").isArray() && metaNode.get("tags").size() > 0) {
                            String inferred = metaNode.get("tags").get(0).asText();
                            if (!inferred.isBlank()) {
                                entity.setCategory(inferred);
                                changed = true;
                                logger.debug("Inferred category from metadata.tags: {}", inferred);
                            }
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to parse metadata JSON for productId={}: {}", entity.getProductId(), ex.getMessage());
                    }
                }
                // Fallback
                if (entity.getCategory() == null || entity.getCategory().isBlank()) {
                    entity.setCategory("Uncategorized");
                    changed = true;
                    logger.debug("Set default category 'Uncategorized' for productId={}", entity.getProductId());
                }
            }

            // 3) Validate price sanity and name presence; record validation status into metadata JSON
            boolean valid = true;
            String invalidReason = null;
            if (entity.getName() == null || entity.getName().isBlank()) {
                valid = false;
                invalidReason = "missing_name";
            } else if (entity.getPrice() == null) {
                valid = false;
                invalidReason = "missing_price";
            } else if (entity.getPrice() < 0.0) {
                valid = false;
                invalidReason = "negative_price";
            }

            // Prepare metadata JSON node
            ObjectNode metadataNode;
            String existingMetadata = entity.getMetadata();
            if (existingMetadata != null && !existingMetadata.isBlank()) {
                try {
                    JsonNode parsed = objectMapper.readTree(existingMetadata);
                    if (parsed != null && parsed.isObject()) {
                        metadataNode = (ObjectNode) parsed;
                    } else {
                        metadataNode = objectMapper.createObjectNode();
                    }
                } catch (Exception ex) {
                    logger.warn("Unable to parse existing metadata; overriding with new JSON. productId={}, error={}", entity.getProductId(), ex.getMessage());
                    metadataNode = objectMapper.createObjectNode();
                }
            } else {
                metadataNode = objectMapper.createObjectNode();
            }

            // Update metadata with validation result
            metadataNode.put("validationStatus", valid ? "READY" : "INVALID");
            if (!valid) {
                metadataNode.put("validationReason", invalidReason != null ? invalidReason : "unspecified");
            } else {
                metadataNode.remove("validationReason");
            }

            // If we adjusted price because it was negative or NaN/Infinite, note it
            if (entity.getPrice() != null && entity.getPrice() < 0.0) {
                // This branch is unlikely since negative price would make isValidEntity false, but keep safety
                metadataNode.put("priceAdjusted", true);
            }

            // Write back metadata if changed or to store validation info
            try {
                String newMetadata = objectMapper.writeValueAsString(metadataNode);
                if (entity.getMetadata() == null || !entity.getMetadata().equals(newMetadata)) {
                    entity.setMetadata(newMetadata);
                    changed = true;
                }
            } catch (Exception ex) {
                logger.warn("Failed to serialize metadata JSON for productId={}: {}", entity.getProductId(), ex.getMessage());
            }

            if (changed) {
                logger.info("ValidateProductProcessor updated productId={} (validationStatus={})", entity.getProductId(), metadataNode.get("validationStatus").asText());
            } else {
                logger.debug("ValidateProductProcessor made no changes for productId={}", entity.getProductId());
            }

        } catch (Exception ex) {
            logger.error("Unexpected error while validating productId={}: {}", entity != null ? entity.getProductId() : "null", ex.getMessage(), ex);
            // Ensure metadata reflects failure
            try {
                ObjectNode failureNode = objectMapper.createObjectNode();
                failureNode.put("validationStatus", "INVALID");
                failureNode.put("validationReason", "processor_error");
                if (entity != null) {
                    entity.setMetadata(objectMapper.writeValueAsString(failureNode));
                }
            } catch (Exception e) {
                logger.error("Failed to write failure metadata for productId={}: {}", entity != null ? entity.getProductId() : "null", e.getMessage());
            }
        }

        return entity;
    }

    private boolean normalizedEquals(double a, Double b) {
        if (b == null) return false;
        return Math.abs(a - b) < 0.000001;
    }
}