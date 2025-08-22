package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class CreateOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateOrderProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.isValid();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();

        // Business logic: reserve inventory for each item snapshot by decrementing Product.availableQuantity
        if (order.getItemsSnapshot() == null || order.getItemsSnapshot().isEmpty()) {
            logger.info("Order {} has no items to reserve", order.getId());
            return order;
        }

        for (Order.Item item : order.getItemsSnapshot()) {
            if (item == null) continue;
            String productId = item.getProductId();
            Integer qtyRequested = item.getQuantity() != null ? item.getQuantity() : 0;

            if (productId == null || productId.isBlank()) {
                logger.error("Order {} contains item with invalid productId", order.getId());
                throw new RuntimeException("Invalid productId in order items");
            }
            if (qtyRequested <= 0) {
                logger.error("Order {} contains item with non-positive quantity for product {}", order.getId(), productId);
                throw new RuntimeException("Invalid quantity for product " + productId);
            }

            // Build simple search condition to find Product by business id (field 'id')
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.id", "EQUALS", productId)
            );

            try {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    condition,
                    true
                );
                ArrayNode results = itemsFuture.get();
                if (results == null || results.size() == 0) {
                    logger.error("Product with id {} not found while processing order {}", productId, order.getId());
                    throw new RuntimeException("Product not found: " + productId);
                }

                // Expect first match to be the product
                ObjectNode found = (ObjectNode) results.get(0);

                // Typical Cyoda response may wrap the entity under "entity"; handle both cases
                JsonNode entityNode = found.has("entity") ? found.get("entity") : found;
                Product product = objectMapper.treeToValue(entityNode, Product.class);

                Integer available = product.getAvailableQuantity() != null ? product.getAvailableQuantity() : 0;
                if (available < qtyRequested) {
                    logger.error("Insufficient stock for product {}: available={}, required={} (order {})",
                        productId, available, qtyRequested, order.getId());
                    throw new RuntimeException("Insufficient stock for product " + productId);
                }

                product.setAvailableQuantity(available - qtyRequested);

                // Extract technicalId from wrapper node if present
                String technicalId = null;
                if (found.has("technicalId") && !found.get("technicalId").isNull()) {
                    technicalId = found.get("technicalId").asText();
                } else if (found.has("metadata") && found.get("metadata").has("technicalId")) {
                    technicalId = found.get("metadata").get("technicalId").asText();
                }

                if (technicalId == null || technicalId.isBlank()) {
                    logger.error("Cannot determine technicalId for product {} to update inventory", productId);
                    throw new RuntimeException("Missing technicalId for product " + productId);
                }

                // Update Product inventory via EntityService (allowed: update other entities)
                CompletableFuture<UUID> updateFuture = entityService.updateItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    product
                );
                updateFuture.get();
                logger.info("Reserved {} units of product {} (new availableQuantity={}) for order {}",
                    qtyRequested, productId, product.getAvailableQuantity(), order.getId());

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while reserving inventory for product {} in order {}", productId, order.getId(), ie);
                throw new RuntimeException(ie);
            } catch (ExecutionException ee) {
                logger.error("Error while reserving inventory for product {} in order {}", productId, order.getId(), ee.getCause());
                throw new RuntimeException(ee.getCause() != null ? ee.getCause() : ee);
            } catch (Exception ex) {
                logger.error("Unexpected error while processing product {} for order {}", productId, order.getId(), ex);
                throw new RuntimeException(ex);
            }
        }

        // Inventory reserved for all items. Further integrations (e.g., notify warehouse) can be triggered by other processors.
        return order;
    }
}