package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.product.version_1.Product;
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

import java.util.Objects;
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

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        Product product = context.entity();
        if (product == null) return null;

        boolean valid = true;

        // 1) Validate price: must be >= 0 (entity.isValid() should already cover this,
        // but we re-check to set availability accordingly)
        if (product.getPrice() == null || product.getPrice() < 0.0) {
            logger.warn("Product {} has invalid price: {}", product.getId(), product.getPrice());
            valid = false;
        }

        // 2) Validate SKU presence
        String sku = product.getSku();
        if (sku == null || sku.isBlank()) {
            logger.warn("Product {} has missing SKU", product.getId());
            valid = false;
        } else {
            // 3) Check SKU uniqueness across Product entities (exclude current product by id)
            try {
                SearchConditionRequest condition = SearchConditionRequest.group(
                    "AND",
                    Condition.of("$.sku", "EQUALS", sku)
                );

                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    condition,
                    true
                );

                ArrayNode items = itemsFuture.join();
                if (items != null) {
                    for (JsonNode node : items) {
                        if (node == null || !node.has("id")) continue;
                        String otherId = node.get("id").asText(null);
                        if (otherId == null) continue;
                        // If another product with same SKU and different id exists -> duplicate
                        if (!Objects.equals(otherId, product.getId())) {
                            logger.warn("Product {} SKU '{}' conflicts with product id {}", product.getId(), sku, otherId);
                            valid = false;
                            break;
                        }
                    }
                }
            } catch (Exception ex) {
                logger.error("Error while checking SKU uniqueness for product {}: {}", product.getId(), ex.getMessage(), ex);
                // In case of error contacting datastore, mark invalid to be safe
                valid = false;
            }
        }

        // 4) Stock threshold: if stock is zero or negative, mark unavailable
        if (product.getStock() != null && product.getStock() <= 0) {
            logger.info("Product {} has non-positive stock ({}). Marking unavailable.", product.getId(), product.getStock());
            valid = false;
        }

        // Set availability based on validation outcome. The entity will be persisted by Cyoda automatically.
        product.setAvailable(valid);

        // Additional logging for final state
        if (valid) {
            logger.info("Product {} validation PASSED. SKU='{}', price={}, stock={}", product.getId(), product.getSku(), product.getPrice(), product.getStock());
        } else {
            logger.info("Product {} validation FAILED. Setting available=false", product.getId());
        }

        return product;
    }
}