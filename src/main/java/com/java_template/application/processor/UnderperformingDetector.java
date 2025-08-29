package com.java_template.application.processor;

import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
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
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class UnderperformingDetector implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UnderperformingDetector.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UnderperformingDetector(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SalesRecord for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(SalesRecord.class)
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

    private boolean isValidEntity(SalesRecord entity) {
        return entity != null && entity.isValid();
    }

    private SalesRecord processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<SalesRecord> context) {
        SalesRecord entity = context.entity();

        try {
            String productIdStr = entity.getProductId();
            if (productIdStr == null || productIdStr.isBlank()) {
                logger.warn("SalesRecord {} has no productId, skipping underperformance detection.", entity.getRecordId());
                return entity;
            }

            UUID productUuid;
            try {
                productUuid = UUID.fromString(productIdStr);
            } catch (IllegalArgumentException iae) {
                logger.warn("ProductId {} in SalesRecord {} is not a valid UUID. Skipping product update.", productIdStr, entity.getRecordId());
                return entity;
            }

            // Fetch the product entity to evaluate its price and update performance metadata
            CompletableFuture<DataPayload> productFuture = entityService.getItem(Product.ENTITY_NAME, Product.ENTITY_VERSION, productUuid);
            DataPayload productPayload = productFuture.get();
            if (productPayload == null || productPayload.getData() == null) {
                logger.warn("Product with id {} not found for SalesRecord {}.", productIdStr, entity.getRecordId());
                return entity;
            }

            Product product = objectMapper.treeToValue(productPayload.getData(), Product.class);
            if (product == null) {
                logger.warn("Failed to deserialize Product payload for id {}.", productIdStr);
                return entity;
            }

            // Compute simple heuristics for underperformance:
            // - low quantity sold (<=1) OR
            // - revenue per unit significantly below product price (less than 50% of price)
            double revenue = entity.getRevenue() != null ? entity.getRevenue() : 0.0;
            int quantity = entity.getQuantity() != null ? entity.getQuantity() : 0;
            double revenuePerUnit = quantity > 0 ? (revenue / quantity) : revenue;
            Double productPrice = product.getPrice();

            boolean lowVolume = quantity <= 1;
            boolean lowRevenuePerUnit;
            if (productPrice != null) {
                lowRevenuePerUnit = revenuePerUnit < (productPrice * 0.5);
            } else {
                // If product price unknown, consider revenuePerUnit threshold at absolute small value
                lowRevenuePerUnit = revenuePerUnit < 1.0;
            }

            boolean underperforming = lowVolume || lowRevenuePerUnit;

            // Update product metadata to include performance tag. Preserve other metadata if present.
            ObjectNode metadataNode;
            String existingMetadata = product.getMetadata();
            if (existingMetadata != null && !existingMetadata.isBlank()) {
                try {
                    JsonNode parsed = objectMapper.readTree(existingMetadata);
                    if (parsed != null && parsed.isObject()) {
                        metadataNode = (ObjectNode) parsed;
                    } else {
                        metadataNode = objectMapper.createObjectNode();
                        metadataNode.put("raw", existingMetadata);
                    }
                } catch (Exception ex) {
                    metadataNode = objectMapper.createObjectNode();
                    metadataNode.put("raw", existingMetadata);
                }
            } else {
                metadataNode = objectMapper.createObjectNode();
            }

            String performanceTag = underperforming ? "UNDERPERFORMING" : "NORMAL";
            metadataNode.put("performance", performanceTag);
            metadataNode.put("lastTaggedBy", className);
            metadataNode.put("lastTaggedAt", java.time.Instant.now().toString());

            product.setMetadata(objectMapper.writeValueAsString(metadataNode));

            // Persist update to the Product entity (allowed: update other entities)
            try {
                entityService.updateItem(UUID.fromString(product.getProductId()), product).get();
                logger.info("Updated product {} with performance tag: {}", product.getProductId(), performanceTag);
            } catch (Exception e) {
                logger.error("Failed to update product {}: {}", product.getProductId(), e.getMessage(), e);
            }

        } catch (Exception e) {
            logger.error("Error during underperforming detection for SalesRecord {}: {}", entity.getRecordId(), e.getMessage(), e);
            // Do not fail the processor; return entity unchanged so Cyoda can continue processing.
        }

        return entity;
    }
}