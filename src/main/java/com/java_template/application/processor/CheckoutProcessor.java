package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
        logger.info("Processing Cart checkout for request: {}", request.getId());

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
        if (cart == null) return null;

        try {
            // 1) Recalculate totals from items and normalize cart.total if needed
            double recalculated = 0.0;
            List<Cart.Item> items = cart.getItems();
            if (items != null) {
                for (Cart.Item it : items) {
                    if (it != null && it.getPriceAtAdd() != null && it.getQuantity() != null) {
                        recalculated += it.getPriceAtAdd() * it.getQuantity();
                    } else {
                        logger.warn("Cart {} contains invalid item, aborting checkout.", cart.getId());
                        cart.setStatus("CheckoutFailed");
                        return cart;
                    }
                }
            }

            if (cart.getTotal() == null || Math.abs(cart.getTotal() - recalculated) > 0.01) {
                logger.info("Cart {} total mismatch (stored={} recalculated={}). Normalizing total.", cart.getId(), cart.getTotal(), recalculated);
                cart.setTotal(recalculated);
            }

            // 2) Validate user if present (must be Active). If missing or inactive => fail checkout.
            String userId = cart.getUserId();
            if (userId != null && !userId.isBlank()) {
                try {
                    UUID userTechId = UUID.fromString(userId);
                    CompletableFuture<ObjectNode> userFuture = entityService.getItem(
                        User.ENTITY_NAME,
                        String.valueOf(User.ENTITY_VERSION),
                        userTechId
                    );
                    ObjectNode userNode = userFuture.join();
                    if (userNode == null) {
                        logger.warn("Cart {} references non-existing user {}", cart.getId(), userId);
                        cart.setStatus("CheckoutFailed");
                        return cart;
                    }
                    User user = objectMapper.treeToValue(userNode, User.class);
                    if (user == null || user.getStatus() == null || !user.getStatus().equalsIgnoreCase("Active")) {
                        logger.warn("User {} not active or invalid for cart {}. Aborting checkout.", userId, cart.getId());
                        cart.setStatus("CheckoutFailed");
                        return cart;
                    }
                } catch (Exception e) {
                    logger.warn("Failed to validate user {} for cart {}: {}", userId, cart.getId(), e.getMessage());
                    cart.setStatus("CheckoutFailed");
                    return cart;
                }
            } else {
                // Guest checkout allowed per prototype; proceed without user validation
                logger.debug("Cart {} is a guest cart (no userId). Proceeding with availability checks.", cart.getId());
            }

            // 3) Check product availability & reserve stock by updating Product entities.
            if (items != null) {
                for (Cart.Item it : items) {
                    String productTechnicalId = it.getProductId();
                    Integer qty = it.getQuantity();
                    if (productTechnicalId == null || productTechnicalId.isBlank() || qty == null || qty <= 0) {
                        logger.warn("Cart {} has invalid product reference or quantity. Aborting checkout.", cart.getId());
                        cart.setStatus("CheckoutFailed");
                        return cart;
                    }

                    try {
                        UUID productTechId = UUID.fromString(productTechnicalId);
                        CompletableFuture<ObjectNode> prodFuture = entityService.getItem(
                            Product.ENTITY_NAME,
                            String.valueOf(Product.ENTITY_VERSION),
                            productTechId
                        );
                        ObjectNode prodNode = prodFuture.join();
                        if (prodNode == null) {
                            logger.warn("Product {} referenced by cart {} not found. Aborting checkout.", productTechnicalId, cart.getId());
                            cart.setStatus("CheckoutFailed");
                            return cart;
                        }
                        Product product = objectMapper.treeToValue(prodNode, Product.class);
                        if (product == null) {
                            logger.warn("Unable to deserialize product {} for cart {}. Aborting checkout.", productTechnicalId, cart.getId());
                            cart.setStatus("CheckoutFailed");
                            return cart;
                        }

                        if (product.getAvailable() == null || !product.getAvailable()) {
                            logger.warn("Product {} is not available for purchase. Aborting checkout for cart {}.", productTechnicalId, cart.getId());
                            cart.setStatus("CheckoutFailed");
                            return cart;
                        }

                        Integer currentStock = product.getStock();
                        if (currentStock == null) currentStock = 0;
                        if (currentStock < qty) {
                            logger.warn("Product {} has insufficient stock (needed={}, available={}). Aborting checkout for cart {}.", productTechnicalId, qty, currentStock, cart.getId());
                            cart.setStatus("CheckoutFailed");
                            return cart;
                        }

                        // Reserve stock: decrement stock and update product entity.
                        int newStock = currentStock - qty;
                        product.setStock(newStock);
                        product.setAvailable(newStock > 0);

                        try {
                            // Update other entity (product) via EntityService - allowed per rules.
                            entityService.updateItem(
                                Product.ENTITY_NAME,
                                String.valueOf(Product.ENTITY_VERSION),
                                UUID.fromString(productTechnicalId),
                                product
                            ).join();
                            logger.info("Reserved {} units of product {} (remaining stock: {}) for cart {}", qty, productTechnicalId, newStock, cart.getId());
                        } catch (Exception e) {
                            logger.error("Failed to update product {} while reserving stock for cart {}: {}", productTechnicalId, cart.getId(), e.getMessage(), e);
                            cart.setStatus("CheckoutFailed");
                            return cart;
                        }

                    } catch (IllegalArgumentException iae) {
                        logger.warn("Invalid product technical id '{}' in cart {}. Aborting checkout.", productTechnicalId, cart.getId());
                        cart.setStatus("CheckoutFailed");
                        return cart;
                    } catch (Exception ex) {
                        logger.error("Unexpected error while validating product {} for cart {}: {}", productTechnicalId, cart.getId(), ex.getMessage(), ex);
                        cart.setStatus("CheckoutFailed");
                        return cart;
                    }
                }
            }

            // 4) If all checks passed, mark cart as CheckedOut
            cart.setStatus("CheckedOut");
            logger.info("Cart {} marked as CheckedOut", cart.getId());
            return cart;

        } catch (Exception ex) {
            logger.error("Error during checkout processing for cart {}: {}", cart != null ? cart.getId() : "unknown", ex.getMessage(), ex);
            if (cart != null) cart.setStatus("CheckoutFailed");
            return cart;
        }
    }
}