package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.CartItem;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.order.version_1.Order.Item;
import com.java_template.application.entity.product.version_1.Product;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class ConvertCartProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ConvertCartProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ConvertCartProcessor(SerializerFactory serializerFactory,
                                EntityService entityService,
                                ObjectMapper objectMapper) {
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
        // Business rules:
        // 1) Cart must have items (Cart.isValid already ensures this)
        // 2) Stock availability: for each cart item, ensure Product.availableQuantity >= quantity
        // 3) Create Order entity as snapshot of cart and persist it
        // 4) Mark cart as CONVERTED (do not call entityService.updateItem for this cart)

        // Validate items presence again defensively
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            logger.error("Cart {} has no items to convert", cart.getId());
            throw new RuntimeException("Cart empty");
        }

        // Check stock for each item
        for (CartItem cartItem : cart.getItems()) {
            if (cartItem == null || cartItem.getProductId() == null || cartItem.getProductId().isBlank()) {
                logger.error("Invalid cart item in cart {}", cart.getId());
                throw new RuntimeException("Invalid cart item");
            }
            try {
                // The EntityService expects a technicalId UUID for getItem
                UUID productTechnicalId = UUID.fromString(cartItem.getProductId());
                CompletableFuture<ObjectNode> productFuture = entityService.getItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    productTechnicalId
                );
                ObjectNode productNode = productFuture.get();
                if (productNode == null) {
                    logger.error("Product {} not found for cart {}", cartItem.getProductId(), cart.getId());
                    throw new RuntimeException("Stock not available for product " + cartItem.getProductId());
                }
                // Convert to Product
                Product product = objectMapper.treeToValue((JsonNode) productNode.get("entity") != null ? productNode.get("entity") : productNode, Product.class);
                if (product == null) {
                    logger.error("Failed to deserialize product {} for cart {}", cartItem.getProductId(), cart.getId());
                    throw new RuntimeException("Stock not available for product " + cartItem.getProductId());
                }
                Integer available = product.getAvailableQuantity();
                if (available == null || available < cartItem.getQuantity()) {
                    logger.error("Insufficient stock for product {}: requested {}, available {}", cartItem.getProductId(), cartItem.getQuantity(), available);
                    throw new RuntimeException("Stock not available for product " + cartItem.getProductId());
                }
            } catch (IllegalArgumentException e) {
                // UUID.fromString failed
                logger.error("Product id {} is not a valid technical UUID for cart {}", cartItem.getProductId(), cart.getId());
                throw new RuntimeException("Stock not available for product " + cartItem.getProductId());
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error while fetching product {} for cart {}: {}", cartItem.getProductId(), cart.getId(), e.getMessage());
                throw new RuntimeException("Error checking stock for product " + cartItem.getProductId());
            } catch (Exception e) {
                logger.error("Unexpected error while checking product {} for cart {}: {}", cartItem.getProductId(), cart.getId(), e.getMessage());
                throw new RuntimeException("Error checking stock for product " + cartItem.getProductId());
            }
        }

        // All items have stock -> create Order snapshot
        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setCartId(cart.getId());
        order.setUserId(cart.getUserId());
        order.setStatus("WAITING_TO_FULFILL");
        order.setTotalAmount(cart.getTotalAmount());
        List<Item> itemsSnapshot = new ArrayList<>();
        for (CartItem ci : cart.getItems()) {
            Item it = new Item();
            it.setProductId(ci.getProductId());
            it.setQuantity(ci.getQuantity());
            it.setUnitPrice(ci.getUnitPrice());
            itemsSnapshot.add(it);
        }
        order.setItemsSnapshot(itemsSnapshot);
        String now = Instant.now().toString();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Order.ENTITY_NAME,
                String.valueOf(Order.ENTITY_VERSION),
                order
            );
            // wait for persistence
            UUID persistedOrderId = idFuture.get();
            logger.info("Created Order {} (technicalId={}) for Cart {}", order.getId(), persistedOrderId, cart.getId());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to persist order for cart {}: {}", cart.getId(), e.getMessage());
            throw new RuntimeException("Failed to create order for cart " + cart.getId());
        }

        // Mark cart as CONVERTED - do not call entityService.updateItem on this cart
        cart.setStatus("CONVERTED");
        cart.setUpdatedAt(Instant.now().toString());

        return cart;
    }
}