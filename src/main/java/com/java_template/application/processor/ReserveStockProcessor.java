package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cartorder.version_1.CartOrder;
import com.java_template.application.entity.cartorder.version_1.CartOrder.Item;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class ReserveStockProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReserveStockProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ReserveStockProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReserveStock for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CartOrder.class)
            .validate(this::isValidEntity, "Invalid cart order state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CartOrder entity) {
        return entity != null && entity.isValid();
    }

    private CartOrder processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CartOrder> context) {
        CartOrder order = context.entity();
        try {
            // Idempotency: if already reserved or beyond, ignore
            String status = order.getStatus();
            if (status != null) {
                String s = status.toLowerCase();
                if (s.equals("stockreserved") || s.equals("confirmed") || s.equals("fulfillment") || s.equals("shipped") || s.equals("completed")) {
                    logger.info("Order {} already processed for stock reservation (status={})", order.getOrderId(), status);
                    return order;
                }
            }

            // Ensure order is in PaymentConfirmed state before reserving
            if (!"PaymentConfirmed".equalsIgnoreCase(order.getStatus())) {
                logger.info("Order {} not in PaymentConfirmed state (current={}), skipping reservation", order.getOrderId(), order.getStatus());
                return order;
            }

            // Attempt to reserve stock for each item
            for (Item item : order.getItems()) {
                // Load product
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.productId", "EQUALS", item.getProductId())
                );
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    condition,
                    true
                );
                ArrayNode results = itemsFuture.get(5, TimeUnit.SECONDS);
                if (results == null || results.size() == 0) {
                    logger.error("Product {} not found while reserving for order {}", item.getProductId(), order.getOrderId());
                    // mark order cancelled
                    order.setStatus("Cancelled");
                    return order;
                }
                ObjectNode prodNode = (ObjectNode) results.get(0);
                Product product = objectMapper.treeToValue(prodNode, Product.class);
                Integer available = product.getStockQuantity();
                if (available == null || available < item.getQuantity()) {
                    logger.error("Insufficient stock for product {} during reserve (requested={}, available={})", item.getProductId(), item.getQuantity(), available);
                    order.setStatus("Cancelled");
                    return order;
                }
            }

            // All items available - decrement stock for each product
            for (Item item : order.getItems()) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.productId", "EQUALS", item.getProductId())
                );
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    condition,
                    true
                );
                ArrayNode results = itemsFuture.get(5, TimeUnit.SECONDS);
                ObjectNode prodNode = (ObjectNode) results.get(0);
                Product product = objectMapper.treeToValue(prodNode, Product.class);
                int newQty = product.getStockQuantity() - item.getQuantity();
                if (newQty < 0) newQty = 0;
                product.setStockQuantity(newQty);

                // Persist product via entityService.updateItem
                // WARNING: The system guideline said never update the current entity automatically persisted by cyoda.
                // Here we are updating Product entity, which is allowed.
                CompletableFuture<java.util.UUID> updateFuture = entityService.updateItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    java.util.UUID.fromString(prodNode.get("technicalId").asText()),
                    objectMapper.valueToTree(product)
                );
                updateFuture.get(5, TimeUnit.SECONDS);
            }

            // Update order status to StockReserved then Confirmed
            order.setStatus("StockReserved");
            logger.info("Order {} stock reserved", order.getOrderId());
            order.setStatus("Confirmed");
            logger.info("Order {} confirmed and ready for fulfillment", order.getOrderId());
            return order;
        } catch (Exception e) {
            logger.error("Error reserving stock for order {}: {}", order.getOrderId(), e.getMessage(), e);
            order.setStatus("Cancelled");
            return order;
        }
    }
}
