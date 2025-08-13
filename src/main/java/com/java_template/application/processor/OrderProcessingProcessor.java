package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.orderitem.version_1.OrderItem;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class OrderProcessingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderProcessingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderProcessingProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid order state for processing")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order order) {
        if (order == null) {
            logger.error("Order entity is null");
            return false;
        }
        if (!"pending".equalsIgnoreCase(order.getStatus())) {
            logger.error("Order status is not pending: {}", order.getStatus());
            return false;
        }
        if (order.getItems() == null || order.getItems().isEmpty()) {
            logger.error("Order has no items");
            return false;
        }
        return true;
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();

        boolean stockSufficient = true;

        // Check stock availability for each OrderItem
        for (OrderItem item : order.getItems()) {
            try {
                CompletableFuture<ObjectNode> productFuture = entityService.getItem(
                        Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION),
                        java.util.UUID.fromString(item.getProductId())
                );
                ObjectNode productNode = productFuture.get();

                if (productNode == null) {
                    logger.error("Product not found for productId: {}", item.getProductId());
                    stockSufficient = false;
                    break;
                }

                Integer stockQuantity = productNode.has("stockQuantity") ? productNode.get("stockQuantity").asInt() : null;
                if (stockQuantity == null || stockQuantity < item.getQuantity()) {
                    logger.error("Insufficient stock for productId: {}. Available: {}, Requested: {}",
                            item.getProductId(), stockQuantity, item.getQuantity());
                    stockSufficient = false;
                    break;
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Failed to get product stock for productId: {}", item.getProductId(), e);
                stockSufficient = false;
                break;
            }
        }

        if (!stockSufficient) {
            order.setStatus("failed");
            logger.error("Order {} failed due to insufficient stock.", order.getOrderId());
            return order;
        }

        // Deduct stock quantities
        for (OrderItem item : order.getItems()) {
            try {
                CompletableFuture<ObjectNode> productFuture = entityService.getItem(
                        Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION),
                        java.util.UUID.fromString(item.getProductId())
                );
                ObjectNode productNode = productFuture.get();

                Integer stockQuantity = productNode.has("stockQuantity") ? productNode.get("stockQuantity").asInt() : 0;
                int newStock = stockQuantity - item.getQuantity();

                // Create new product node with updated stock
                ObjectNode updatedProduct = productNode.deepCopy();
                updatedProduct.put("stockQuantity", newStock);

                // Persist updated product stock
                entityService.addItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), updatedProduct);

                logger.info("Deducted {} units from product {}. New stock: {}",
                        item.getQuantity(), item.getProductId(), newStock);
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Failed to deduct stock for productId: {}", item.getProductId(), e);
                order.setStatus("failed");
                return order;
            }
        }

        // Update related Cart status to CHECKED_OUT
        try {
            CompletableFuture<ArrayNode> cartsFuture = entityService.getItemsByCondition(
                    Cart.ENTITY_NAME, String.valueOf(Cart.ENTITY_VERSION),
                    SearchConditionRequest.group("AND", Condition.of("$.cartId", "EQUALS", order.getOrderId())),
                    true
            );
            ArrayNode carts = cartsFuture.get();
            if (carts != null && carts.size() > 0) {
                ObjectNode cartNode = (ObjectNode) carts.get(0);
                cartNode.put("status", "checked_out");
                entityService.addItem(Cart.ENTITY_NAME, String.valueOf(Cart.ENTITY_VERSION), cartNode);
                logger.info("Updated Cart status to CHECKED_OUT for cartId: {}", order.getOrderId());
            } else {
                logger.warn("No Cart found for orderId: {} to update status", order.getOrderId());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to update Cart status for orderId: {}", order.getOrderId(), e);
        }

        order.setStatus("completed");
        logger.info("Order {} processed successfully", order.getOrderId());

        return order;
    }
}
