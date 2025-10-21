package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Processor for creating orders from paid carts including
 * cart snapshot, stock decrementing, and shipment creation.
 */
@Component
public class CreateOrderFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateOrderFromPaidProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processOrderCreation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Process order creation from paid cart
     */
    private EntityWithMetadata<Order> processOrderCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing order creation for order: {}", order.getOrderId());

        try {
            // Find the associated cart (assuming orderId contains cart reference or we need to find it)
            Cart cart = findAssociatedCart(order);
            if (cart != null) {
                // Decrement product stock for each line item
                decrementProductStock(cart);

                // Create shipment for the order
                createShipmentForOrder(order, cart);

                // Mark cart as converted
                markCartAsConverted(cart);
            }

            // Set order status and timestamps
            order.setStatus("WAITING_TO_FULFILL");
            order.setUpdatedAt(LocalDateTime.now());

            logger.info("Order created successfully: {}", order.getOrderId());

        } catch (Exception e) {
            logger.error("Failed to process order creation for order: {}", order.getOrderId(), e);
            // In a real system, you might want to handle this more gracefully
            throw new RuntimeException("Order creation failed", e);
        }

        return entityWithMetadata;
    }

    /**
     * Find the associated cart for this order
     * Note: This is a simplified implementation - in practice you might store cart reference in order
     */
    private Cart findAssociatedCart(Order order) {
        try {
            // For this demo, we'll assume the orderId contains cart information or we search by guest contact
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            
            // Search for cart in CHECKING_OUT status with matching guest contact
            SimpleCondition statusCondition = new SimpleCondition()
                    .withJsonPath("$.status")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree("CHECKING_OUT"));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(statusCondition));

            List<EntityWithMetadata<Cart>> carts = entityService.search(cartModelSpec, condition, Cart.class);
            
            // Find cart with matching guest contact (simplified matching by name)
            return carts.stream()
                    .map(EntityWithMetadata::entity)
                    .filter(cart -> cart.getGuestContact() != null && 
                                  order.getGuestContact() != null &&
                                  order.getGuestContact().getName().equals(cart.getGuestContact().getName()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            logger.error("Failed to find associated cart for order: {}", order.getOrderId(), e);
            return null;
        }
    }

    /**
     * Decrement product stock for each cart line item
     */
    private void decrementProductStock(Cart cart) {
        ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
        
        for (Cart.CartLine line : cart.getLines()) {
            try {
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                        productModelSpec, line.getSku(), "sku", Product.class);
                
                if (productWithMetadata != null) {
                    Product product = productWithMetadata.entity();
                    int newQuantity = Math.max(0, product.getQuantityAvailable() - line.getQty());
                    product.setQuantityAvailable(newQuantity);
                    
                    entityService.update(productWithMetadata.metadata().getId(), product, "update_inventory");
                    logger.debug("Decremented stock for SKU {}: {} -> {}", 
                               line.getSku(), product.getQuantityAvailable() + line.getQty(), newQuantity);
                }
            } catch (Exception e) {
                logger.error("Failed to decrement stock for SKU: {}", line.getSku(), e);
            }
        }
    }

    /**
     * Create shipment for the order
     */
    private void createShipmentForOrder(Order order, Cart cart) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId(generateShipmentId());
            shipment.setOrderId(order.getOrderId());
            shipment.setStatus("PICKING");
            
            // Convert cart lines to shipment lines
            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            for (Cart.CartLine cartLine : cart.getLines()) {
                Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
                shipmentLine.setSku(cartLine.getSku());
                shipmentLine.setQtyOrdered(cartLine.getQty());
                shipmentLine.setQtyPicked(0);
                shipmentLine.setQtyShipped(0);
                shipmentLines.add(shipmentLine);
            }
            shipment.setLines(shipmentLines);
            
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());
            
            entityService.create(shipment);
            logger.info("Shipment created for order: {} with shipment ID: {}", order.getOrderId(), shipment.getShipmentId());
        } catch (Exception e) {
            logger.error("Failed to create shipment for order: {}", order.getOrderId(), e);
        }
    }

    /**
     * Mark cart as converted
     */
    private void markCartAsConverted(Cart cart) {
        try {
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cart.getCartId(), "cartId", Cart.class);
            
            if (cartWithMetadata != null) {
                cart.setStatus("CONVERTED");
                cart.setUpdatedAt(LocalDateTime.now());
                entityService.update(cartWithMetadata.metadata().getId(), cart, "checkout");
                logger.debug("Cart marked as converted: {}", cart.getCartId());
            }
        } catch (Exception e) {
            logger.error("Failed to mark cart as converted: {}", cart.getCartId(), e);
        }
    }

    /**
     * Generate unique shipment ID
     */
    private String generateShipmentId() {
        return "SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
