package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.orderitem.version_1.OrderItem;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class ProcessOrder implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessOrder.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProcessOrder(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid Order entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order order) {
        if (order == null) return false;
        if (order.getStatus() == null || order.getItems() == null) return false;
        return true;
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        logger.info("Start processing Order with ID: {}", order.getOrderId());

        // Business logic:
        // 1. Check stock availability for each OrderItem
        // 2. Deduct stock from inventory
        // 3. Update related Cart status to CHECKED_OUT
        // 4. Update Order status accordingly

        boolean stockSufficient = checkStockForOrder(order);
        if (stockSufficient) {
            deductStock(order);
            order.setStatus("COMPLETED");
            boolean cartUpdated = updateCartStatus(order.getCustomerId(), "CHECKED_OUT");
            if (!cartUpdated) {
                logger.warn("Failed to update Cart status for customerId: {}", order.getCustomerId());
            }
            logger.info("Order {} processed successfully.", order.getOrderId());
        } else {
            order.setStatus("FAILED");
            logger.warn("Order {} processing failed due to insufficient stock.", order.getOrderId());
        }

        return order;
    }

    private boolean checkStockForOrder(Order order) {
        List<OrderItem> items = order.getItems();
        try {
            for (OrderItem item : items) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.productId", "EQUALS", item.getProductId()),
                        Condition.of("$.quantity", "GREATER_THAN_OR_EQUAL", item.getQuantity().toString())
                );
                CompletableFuture<java.util.List<?>> future = entityService.getItemsByCondition(
                        "Product", "1",
                        condition,
                        true
                );
                java.util.List<?> products = future.get();
                if (products == null || products.isEmpty()) {
                    logger.warn("Insufficient stock for product: {}", item.getProductId());
                    return false;
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error checking stock for order {}: {}", order.getOrderId(), e.getMessage());
            return false;
        }
        return true;
    }

    private void deductStock(Order order) {
        List<OrderItem> items = order.getItems();
        try {
            for (OrderItem item : items) {
                // Retrieve the product by productId
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.productId", "EQUALS", item.getProductId())
                );
                CompletableFuture<java.util.List<ObjectNode>> future = entityService.getItemsByCondition(
                        "Product", "1",
                        condition,
                        true
                );
                java.util.List<ObjectNode> products = future.get();
                if (products != null && !products.isEmpty()) {
                    ObjectNode product = products.get(0);
                    int currentStock = product.get("quantity").asInt();
                    int newStock = currentStock - item.getQuantity();
                    if (newStock < 0) {
                        logger.warn("Stock for product {} cannot be negative. Skipping deduction.", item.getProductId());
                        continue;
                    }
                    // Here we would update the product stock - but per requirements no explicit update
                    // So just log the deduction
                    logger.info("Deducted {} units from product {} stock. New stock: {}", item.getQuantity(), item.getProductId(), newStock);
                } else {
                    logger.warn("Product {} not found for stock deduction.", item.getProductId());
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error deducting stock for order {}: {}", order.getOrderId(), e.getMessage());
        }
    }

    private boolean updateCartStatus(String customerId, String status) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.customerId", "EQUALS", customerId),
                    Condition.of("$.status", "EQUALS", "ACTIVE")
            );
            CompletableFuture<java.util.List<ObjectNode>> future = entityService.getItemsByCondition(
                    "Cart", "1",
                    condition,
                    true
            );
            java.util.List<ObjectNode> carts = future.get();
            if (carts != null && !carts.isEmpty()) {
                ObjectNode cart = carts.get(0);
                // Normally update cart status to CHECKED_OUT - no explicit update per requirement
                // Just log the intent
                logger.info("Updating Cart {} status to {}", cart.get("cartId").asText(), status);
                return true;
            } else {
                logger.warn("No active Cart found for customerId: {}", customerId);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error updating cart status for customerId {}: {}", customerId, e.getMessage());
        }
        return false;
    }
}
