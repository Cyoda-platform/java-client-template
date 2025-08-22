package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.product.version_1.Product;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Component
public class ConvertCartProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ConvertCartProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ConvertCartProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Business validations
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            logger.error("Cart {} has no items to convert", cart.getId());
            throw new RuntimeException("Cart empty");
        }

        // Stock validation: ensure for each cart item the product has enough availableQuantity
        for (Cart.CartItem cartItem : cart.getItems()) {
            if (cartItem == null || cartItem.getProductId() == null || cartItem.getProductId().isBlank()) {
                logger.error("Invalid cart item in cart {}", cart.getId());
                throw new RuntimeException("Invalid cart item");
            }

            UUID productTechnicalId;
            try {
                productTechnicalId = UUID.fromString(cartItem.getProductId());
            } catch (IllegalArgumentException iae) {
                logger.error("Product id {} is not a valid technical UUID for cart {}", cartItem.getProductId(), cart.getId());
                throw new RuntimeException("Invalid product technical id");
            }

            try {
                ObjectNode prodNode = entityService.getItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    productTechnicalId
                ).join();

                if (prodNode == null) {
                    logger.error("Product {} not found for cart {}", cartItem.getProductId(), cart.getId());
                    throw new RuntimeException("Stock not available for product " + cartItem.getProductId());
                }

                Product product = objectMapper.treeToValue(prodNode, Product.class);
                if (product == null) {
                    logger.error("Failed to deserialize product {} for cart {}", cartItem.getProductId(), cart.getId());
                    throw new RuntimeException("Stock not available for product " + cartItem.getProductId());
                }

                Integer available = product.getAvailableQuantity();
                Integer required = cartItem.getQuantity() == null ? 0 : cartItem.getQuantity();
                if (available == null || available < required) {
                    logger.error("Insufficient stock for product {}: requested {}, available {}", cartItem.getProductId(), required, available);
                    throw new RuntimeException("Stock not available for product " + cartItem.getProductId());
                }
            } catch (CompletionException ce) {
                logger.error("Error while fetching product {} for cart {}: {}", cartItem.getProductId(), cart.getId(), ce.getMessage());
                throw new RuntimeException("Error checking stock for product " + cartItem.getProductId());
            } catch (Exception e) {
                logger.error("Unexpected error while checking product {} for cart {}: {}", cartItem.getProductId(), cart.getId(), e.getMessage());
                throw new RuntimeException("Error checking stock for product " + cartItem.getProductId());
            }
        }

        // All validations passed, create Order snapshot
        Order order = new Order();
        String now = Instant.now().toString();
        order.setId(UUID.randomUUID().toString());
        order.setCartId(cart.getId());
        order.setUserId(cart.getUserId());
        order.setStatus("WAITING_TO_FULFILL");
        order.setTotalAmount(cart.getTotalAmount());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        List<Order.Item> itemsSnapshot = new ArrayList<>();
        for (Cart.CartItem ci : cart.getItems()) {
            Order.Item it = new Order.Item();
            it.setProductId(ci.getProductId());
            it.setQuantity(ci.getQuantity());
            it.setUnitPrice(ci.getUnitPrice());
            itemsSnapshot.add(it);
        }
        order.setItemsSnapshot(itemsSnapshot);

        // Persist the new Order entity (do not update the triggering cart via entityService)
        try {
            UUID persistedOrderId = entityService.addItem(
                Order.ENTITY_NAME,
                String.valueOf(Order.ENTITY_VERSION),
                order
            ).join();
            logger.info("Created Order {} (technicalId={}) for Cart {}", order.getId(), persistedOrderId, cart.getId());
        } catch (CompletionException ce) {
            logger.error("Failed to persist order for cart {}: {}", cart.getId(), ce.getMessage());
            throw new RuntimeException("Failed to create order for cart " + cart.getId());
        } catch (Exception e) {
            logger.error("Failed to persist order for cart {}: {}", cart.getId(), e.getMessage());
            throw new RuntimeException("Failed to create order for cart " + cart.getId());
        }

        // Transition cart to CONVERTED
        cart.setStatus("CONVERTED");
        cart.setUpdatedAt(Instant.now().toString());

        return cart;
    }
}