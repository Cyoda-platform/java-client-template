package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.Item;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CheckoutProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CheckoutProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart entity) {
        return entity != null && entity.isValid();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        // Recalculate total to ensure consistency
        double recalculated = 0.0;
        List<Item> items = cart.getItems();
        if (items != null) {
            for (Item it : items) {
                if (it != null && it.getPriceAtAdd() != null && it.getQuantity() != null) {
                    recalculated += it.getPriceAtAdd() * it.getQuantity();
                }
            }
        }
        if (cart.getTotal() == null || Math.abs(cart.getTotal() - recalculated) > 0.01) {
            cart.setTotal(recalculated);
            logger.info("Cart total recalculated to {}", recalculated);
        }

        // Validate user existence and status
        String userId = cart.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("Checkout requires a valid userId (guest checkout not supported)");
        }

        try {
            ObjectNode userNode = entityService.getItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                UUID.fromString(userId)
            ).join();

            if (userNode == null) {
                throw new IllegalStateException("User not found: " + userId);
            }

            User user = objectMapper.convertValue(userNode, User.class);
            if (user.getStatus() == null || !user.getStatus().equalsIgnoreCase("Active")) {
                throw new IllegalStateException("User is not active: " + userId);
            }

            // Validate each item availability and reserve stock by updating Product entities
            if (items != null) {
                for (Item it : items) {
                    if (it == null) {
                        throw new IllegalStateException("Invalid cart item detected");
                    }
                    String productTechnicalId = it.getProductId();
                    if (productTechnicalId == null || productTechnicalId.isBlank()) {
                        throw new IllegalStateException("Cart item missing productId");
                    }

                    ObjectNode productNode = entityService.getItem(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        UUID.fromString(productTechnicalId)
                    ).join();

                    if (productNode == null) {
                        throw new IllegalStateException("Product not found: " + productTechnicalId);
                    }

                    Product product = objectMapper.convertValue(productNode, Product.class);

                    if (product.getAvailable() == null || !product.getAvailable()) {
                        throw new IllegalStateException("Product not available for sale: " + productTechnicalId);
                    }

                    Integer currentStock = product.getStock();
                    Integer qty = it.getQuantity();
                    if (currentStock == null || qty == null || currentStock < qty) {
                        throw new IllegalStateException("Insufficient stock for product: " + productTechnicalId);
                    }

                    // Reserve stock by decrementing and updating Product entity
                    product.setStock(currentStock - qty);
                    entityService.updateItem(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        UUID.fromString(productTechnicalId),
                        product
                    ).join();

                    logger.info("Reserved {} units of product {} (remaining stock: {})", qty, productTechnicalId, product.getStock());
                }
            }

            // All checks passed: mark cart as CheckedOut
            cart.setStatus("CheckedOut");
            logger.info("Cart {} marked as CheckedOut", cart.getId());

            return cart;

        } catch (RuntimeException ex) {
            logger.error("Error during checkout processing for cart {}: {}", cart.getId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}