package com.java_template.application.processor;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class ProductValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ProductValidationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Product.class)
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

        // Normalize/trimming textual fields
        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }
        if (entity.getSku() != null) {
            entity.setSku(entity.getSku().trim());
        }
        if (entity.getCategory() != null) {
            entity.setCategory(entity.getCategory().trim());
        }
        if (entity.getDescription() != null) {
            entity.setDescription(entity.getDescription().trim());
        }

        // Ensure numeric fields are sane: price to 2 decimals, quantityAvailable not null
        if (entity.getPrice() != null) {
            double rounded = Math.round(entity.getPrice() * 100.0) / 100.0;
            entity.setPrice(rounded);
        }
        if (entity.getQuantityAvailable() == null) {
            entity.setQuantityAvailable(0);
        }

        // Business rule: SKU must be unique (case-insensitive). If another product exists with same SKU, fail processing.
        try {
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.sku", "IEQUALS", entity.getSku() == null ? "" : entity.getSku())
            );

            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(
                Product.ENTITY_NAME,
                String.valueOf(Product.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode items = future.get();
            if (items != null && items.size() > 0) {
                for (int i = 0; i < items.size(); i++) {
                    ObjectNode node = (ObjectNode) items.get(i);
                    // If we find an existing product with same SKU and different productId -> conflict
                    if (node != null && node.has("productId")) {
                        String existingProductId = node.get("productId").asText();
                        String currentProductId = entity.getProductId();
                        // If creating a new product (no productId) or the ids differ -> duplicate SKU
                        if (currentProductId == null || currentProductId.isBlank() || !currentProductId.equals(existingProductId)) {
                            String msg = "SKU already exists for another product: " + entity.getSku();
                            logger.error(msg);
                            throw new IllegalStateException(msg);
                        }
                    } else {
                        // If node has no productId, treat as duplicate as well
                        String msg = "SKU already exists for another product: " + entity.getSku();
                        logger.error(msg);
                        throw new IllegalStateException(msg);
                    }
                }
            }
        } catch (IllegalStateException ex) {
            // rethrow to fail processing with clear reason
            throw ex;
        } catch (Exception ex) {
            logger.error("Failed to validate product SKU uniqueness for sku={}: {}", entity.getSku(), ex.getMessage(), ex);
            throw new RuntimeException("Failed to validate product SKU uniqueness", ex);
        }

        // All validations and normalization passed - return modified entity to be persisted by Cyoda
        return entity;
    }
}