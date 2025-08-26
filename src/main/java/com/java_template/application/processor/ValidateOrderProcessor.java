package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.order.version_1.Order.Item;
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

import java.util.UUID;

@Component
public class ValidateOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ValidateOrderProcessor(SerializerFactory serializerFactory,
                                  EntityService entityService,
                                  ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        // Default to cancelled if anything goes wrong during validation/reservation
        try {
            // Iterate items and validate stock
            for (Item it : order.getItems()) {
                String sku = it.getProductSku();
                Integer qty = it.getQuantity();

                if (sku == null || sku.isBlank() || qty == null || qty <= 0) {
                    logger.warn("Order {} has invalid item data (sku={}, qty={}) -> cancelling", order.getOrderId(), sku, qty);
                    order.setStatus("Cancelled");
                    return order;
                }

                // Build simple condition to find product by sku
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.sku", "EQUALS", sku)
                );

                ArrayNode matches = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    condition,
                    true
                ).join();

                if (matches == null || matches.isEmpty()) {
                    logger.warn("Product with sku {} not found for order {} -> cancelling", sku, order.getOrderId());
                    order.setStatus("Cancelled");
                    return order;
                }

                // Use first match
                ObjectNode productNode = (ObjectNode) matches.get(0);
                Product product = objectMapper.treeToValue(productNode, Product.class);

                if (product.getAvailableQuantity() == null || product.getAvailableQuantity() < qty) {
                    logger.warn("Insufficient stock for sku {} (available={}, required={}) -> cancelling order {}",
                        sku, product.getAvailableQuantity(), qty, order.getOrderId());
                    order.setStatus("Cancelled");
                    return order;
                }

                // Reserve stock by decrementing availableQuantity and updating product entity
                int newQty = product.getAvailableQuantity() - qty;
                product.setAvailableQuantity(newQty);

                // Attempt to obtain technical id for update. Try common fields "technicalId" then "id"
                String technicalIdStr = null;
                if (productNode.has("technicalId") && !productNode.get("technicalId").isNull()) {
                    technicalIdStr = productNode.get("technicalId").asText(null);
                } else if (productNode.has("id") && !productNode.get("id").isNull()) {
                    technicalIdStr = productNode.get("id").asText(null);
                }

                if (technicalIdStr != null && !technicalIdStr.isBlank()) {
                    try {
                        entityService.updateItem(
                            Product.ENTITY_NAME,
                            String.valueOf(Product.ENTITY_VERSION),
                            UUID.fromString(technicalIdStr),
                            product
                        ).join();
                    } catch (Exception ex) {
                        logger.error("Failed to update product {} after reserving stock for order {}: {}", sku, order.getOrderId(), ex.getMessage(), ex);
                        order.setStatus("Cancelled");
                        return order;
                    }
                } else {
                    // If no technical id available, log and cancel to avoid inconsistent state
                    logger.error("Cannot determine technicalId for product {} while processing order {} -> cancelling", sku, order.getOrderId());
                    order.setStatus("Cancelled");
                    return order;
                }
            }

            // All items validated and stock reserved
            order.setStatus("Confirmed");
            logger.info("Order {} validated and confirmed", order.getOrderId());
            return order;

        } catch (Exception e) {
            logger.error("Exception while validating order {}: {}", order.getOrderId(), e.getMessage(), e);
            order.setStatus("Cancelled");
            return order;
        }
    }
}