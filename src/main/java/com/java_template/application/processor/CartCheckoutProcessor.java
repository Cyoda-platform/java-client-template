package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cartitem.version_1.CartItem;
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
public class CartCheckoutProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartCheckoutProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CartCheckoutProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart checkout for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart state for checkout")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        if (cart == null) {
            logger.error("Cart entity is null");
            return false;
        }
        if (!"active".equalsIgnoreCase(cart.getStatus())) {
            logger.error("Cart status is not active: {}", cart.getStatus());
            return false;
        }
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            logger.error("Cart has no items");
            return false;
        }
        return true;
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        // Business logic:
        // 1. Verify stock availability for each CartItem
        // 2. If stock insufficient, log error and fail process
        // 3. If stock sufficient, deduct stock quantities
        // 4. Update Cart status to CHECKED_OUT
        // 5. Persist changes as needed (handled by framework)

        boolean stockSufficient = true;
        for (CartItem item : cart.getItems()) {
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
            throw new IllegalStateException("Insufficient stock for one or more cart items");
        }

        // Deduct stock quantities
        for (CartItem item : cart.getItems()) {
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
                throw new IllegalStateException("Failed to deduct stock for productId: " + item.getProductId());
            }
        }

        cart.setStatus("checked_out");
        logger.info("Cart {} checked out successfully", cart.getCartId());
        return cart;
    }
}
