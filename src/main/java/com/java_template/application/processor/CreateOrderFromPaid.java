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
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Processor to create order from paid payment.
 * Snapshots cart data, decrements product inventory, and creates shipment.
 */
@Component
public class CreateOrderFromPaid implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaid.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateOrderFromPaid(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
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
     * Validates the EntityWithMetadata wrapper for Order
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && technicalId != null;
    }

    /**
     * Creates order from paid payment, decrements stock, and creates shipment
     */
    private EntityWithMetadata<Order> processOrderCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid payment: {}", order.getOrderId());

        try {
            // Find the associated cart (assuming orderId contains cartId reference)
            Cart cart = findCartForOrder(order);
            if (cart == null) {
                throw new RuntimeException("Cart not found for order: " + order.getOrderId());
            }

            // Snapshot cart data into order
            snapshotCartToOrder(order, cart);

            // Decrement product inventory
            decrementProductInventory(cart);

            // Create shipment
            createShipmentForOrder(order);

            // Set order status and timestamps
            order.setStatus("WAITING_TO_FULFILL");
            LocalDateTime now = LocalDateTime.now();
            if (order.getCreatedAt() == null) {
                order.setCreatedAt(now);
            }
            order.setUpdatedAt(now);

            logger.info("Order {} created successfully from cart", order.getOrderId());

        } catch (Exception e) {
            logger.error("Failed to create order {}: {}", order.getOrderId(), e.getMessage());
            throw new RuntimeException("Order creation failed", e);
        }

        return entityWithMetadata;
    }

    /**
     * Find cart associated with the order
     */
    private Cart findCartForOrder(Order order) {
        try {
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);

            // Assuming orderId contains cartId reference - this is a simplified approach
            // In real implementation, you'd have a proper relationship field
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.cartId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(order.getOrderId()));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            List<EntityWithMetadata<Cart>> carts = entityService.search(cartModelSpec, groupCondition, Cart.class);
            
            return carts.isEmpty() ? null : carts.get(0).entity();
        } catch (Exception e) {
            logger.error("Error finding cart for order {}: {}", order.getOrderId(), e.getMessage());
            return null;
        }
    }

    /**
     * Snapshot cart data into order
     */
    private void snapshotCartToOrder(Order order, Cart cart) {
        // Convert cart lines to order lines
        List<Order.OrderLine> orderLines = new ArrayList<>();
        if (cart.getLines() != null) {
            for (Cart.CartLine cartLine : cart.getLines()) {
                Order.OrderLine orderLine = new Order.OrderLine();
                orderLine.setSku(cartLine.getSku());
                orderLine.setName(cartLine.getName());
                orderLine.setUnitPrice(cartLine.getPrice());
                orderLine.setQty(cartLine.getQty());
                orderLine.setLineTotal(cartLine.getLineTotal());
                orderLines.add(orderLine);
            }
        }
        order.setLines(orderLines);

        // Set order totals
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(cart.getGrandTotal());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);

        // Copy guest contact information
        if (cart.getGuestContact() != null) {
            Order.GuestContact guestContact = new Order.GuestContact();
            guestContact.setName(cart.getGuestContact().getName());
            guestContact.setEmail(cart.getGuestContact().getEmail());
            guestContact.setPhone(cart.getGuestContact().getPhone());
            
            if (cart.getGuestContact().getAddress() != null) {
                Order.Address address = new Order.Address();
                address.setLine1(cart.getGuestContact().getAddress().getLine1());
                address.setCity(cart.getGuestContact().getAddress().getCity());
                address.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                address.setCountry(cart.getGuestContact().getAddress().getCountry());
                guestContact.setAddress(address);
            }
            
            order.setGuestContact(guestContact);
        }

        // Generate short ULID for order number (simplified)
        order.setOrderNumber(generateShortULID());
    }

    /**
     * Decrement product inventory for ordered items
     */
    private void decrementProductInventory(Cart cart) {
        if (cart.getLines() == null) return;

        ModelSpec productModelSpec = new ModelSpec()
                .withName(Product.ENTITY_NAME)
                .withVersion(Product.ENTITY_VERSION);

        for (Cart.CartLine line : cart.getLines()) {
            try {
                // Find product by SKU
                SimpleCondition condition = new SimpleCondition()
                        .withJsonPath("$.sku")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(line.getSku()));

                GroupCondition groupCondition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(condition));

                List<EntityWithMetadata<Product>> products = entityService.search(productModelSpec, groupCondition, Product.class);
                
                if (!products.isEmpty()) {
                    EntityWithMetadata<Product> productWithMetadata = products.get(0);
                    Product product = productWithMetadata.entity();
                    
                    // Decrement quantity available
                    int currentQty = product.getQuantityAvailable() != null ? product.getQuantityAvailable() : 0;
                    int orderedQty = line.getQty() != null ? line.getQty() : 0;
                    int newQty = Math.max(0, currentQty - orderedQty);
                    
                    product.setQuantityAvailable(newQty);
                    
                    // Update product
                    entityService.update(productWithMetadata.metadata().getId(), product, "update_inventory");
                    
                    logger.info("Decremented inventory for SKU {}: {} -> {}", line.getSku(), currentQty, newQty);
                }
            } catch (Exception e) {
                logger.error("Failed to decrement inventory for SKU {}: {}", line.getSku(), e.getMessage());
            }
        }
    }

    /**
     * Create shipment for the order
     */
    private void createShipmentForOrder(Order order) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId(UUID.randomUUID().toString());
            shipment.setOrderId(order.getOrderId());
            shipment.setStatus("PICKING");
            
            // Create shipment lines from order lines
            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            if (order.getLines() != null) {
                for (Order.OrderLine orderLine : order.getLines()) {
                    Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
                    shipmentLine.setSku(orderLine.getSku());
                    shipmentLine.setQtyOrdered(orderLine.getQty());
                    shipmentLine.setQtyPicked(0);
                    shipmentLine.setQtyShipped(0);
                    shipmentLines.add(shipmentLine);
                }
            }
            shipment.setLines(shipmentLines);
            
            LocalDateTime now = LocalDateTime.now();
            shipment.setCreatedAt(now);
            shipment.setUpdatedAt(now);
            
            // Create shipment entity
            entityService.create(shipment);
            
            logger.info("Created shipment {} for order {}", shipment.getShipmentId(), order.getOrderId());
            
        } catch (Exception e) {
            logger.error("Failed to create shipment for order {}: {}", order.getOrderId(), e.getMessage());
        }
    }

    /**
     * Generate a short ULID-like order number (simplified implementation)
     */
    private String generateShortULID() {
        // Simplified ULID generation - in production use proper ULID library
        return "ORD-" + System.currentTimeMillis() % 1000000;
    }
}
