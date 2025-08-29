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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Component
public class IndexProductProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IndexProductProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public IndexProductProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        return entity != null && entity.isValid();
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product entity = context.entity();
        try {
            // Enrichment: normalize name and category
            if (entity.getName() != null) {
                String trimmed = entity.getName().trim();
                if (!trimmed.equals(entity.getName())) {
                    entity.setName(trimmed);
                }
            }

            if (entity.getCategory() != null) {
                String trimmedCat = entity.getCategory().trim();
                // normalize category to Title Case (simple approach)
                String[] parts = trimmedCat.split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    String p = parts[i];
                    if (p.length() > 0) {
                        sb.append(Character.toUpperCase(p.charAt(0)));
                        if (p.length() > 1) sb.append(p.substring(1).toLowerCase());
                    }
                    if (i < parts.length - 1) sb.append(" ");
                }
                String normalized = sb.toString();
                if (!normalized.equals(entity.getCategory())) {
                    entity.setCategory(normalized);
                }
            }

            // Price normalization: round to 2 decimal places if present
            if (entity.getPrice() != null) {
                try {
                    BigDecimal bd = BigDecimal.valueOf(entity.getPrice());
                    bd = bd.setScale(2, RoundingMode.HALF_UP);
                    double normalizedPrice = bd.doubleValue();
                    if (Double.compare(normalizedPrice, entity.getPrice()) != 0) {
                        entity.setPrice(normalizedPrice);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to normalize price for product {}: {}", entity.getProductId(), e.getMessage());
                }
            }

            // Build or augment metadata for indexing purposes
            ObjectNode metaNode = objectMapper.createObjectNode();
            // track indexing timestamp
            metaNode.put("indexedAt", Instant.now().toString());
            // include normalized fields for search/indexing
            if (entity.getCategory() != null) metaNode.put("category_normalized", entity.getCategory());
            if (entity.getName() != null) metaNode.put("name_normalized", entity.getName());
            if (entity.getPrice() != null) metaNode.put("price_normalized", entity.getPrice());

            // if existing metadata is valid JSON, attach it under originalMetadata to preserve source
            String existingMetadata = entity.getMetadata();
            if (existingMetadata != null && !existingMetadata.isBlank()) {
                try {
                    JsonNode existingNode = objectMapper.readTree(existingMetadata);
                    metaNode.set("originalMetadata", existingNode);
                } catch (Exception ex) {
                    // not JSON, store as raw string
                    metaNode.put("originalMetadataRaw", existingMetadata);
                }
            }

            // Add a basic validation hint for downstream consumers
            if (entity.isValid()) {
                metaNode.put("validation", "VALID");
            } else {
                metaNode.put("validation", "INVALID");
            }

            // Persist metadata back as string
            try {
                String metaString = objectMapper.writeValueAsString(metaNode);
                entity.setMetadata(metaString);
            } catch (Exception ex) {
                logger.warn("Failed to serialize metadata for product {}: {}", entity.getProductId(), ex.getMessage());
            }

            // Note: per rules, do not add/update/delete the triggering entity via EntityService.
            // The Cyoda platform will persist changes to this entity automatically.
            // Optionally, we could produce additional side-effects (e.g., indexing to external search)
            // but this processor focuses on enrichment and preparing entity for indexing.

        } catch (Exception ex) {
            logger.error("Unexpected error while processing Product {}: {}", entity != null ? entity.getProductId() : "null", ex.getMessage(), ex);
        }

        return entity;
    }
}