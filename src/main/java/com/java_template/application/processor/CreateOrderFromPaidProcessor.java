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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: Processor that creates orders from paid carts, snapshots cart data,
 * decrements product inventory, and creates shipments for order fulfillment.
 */
@Component
public class CreateOrderFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateOrderFromPaidProcessor(SerializerFactory serializerFactory, 
                                       EntityService entityService, 
                                       ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing order creation from paid cart for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::createOrderFromPaidCart)
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
        return order != null && order.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Creates order from paid cart, decrements inventory, and creates shipment
     */
    private EntityWithMetadata<Order> createOrderFromPaidCart(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid cart for order: {}", order.getOrderId());

        try {
            // Find the associated cart (assuming orderId matches cartId for this demo)
            Cart cart = findCartById(order.getOrderId());
            if (cart == null) {
                logger.error("Cart not found for order: {}", order.getOrderId());
                return entityWithMetadata;
            }

            // Snapshot cart data into order
            snapshotCartToOrder(cart, order);

            // Decrement product inventory
            decrementProductInventory(order.getLines());

            // Create shipment
            createShipmentForOrder(order);

            // Update order timestamps
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            logger.info("Order {} created successfully from cart with {} items", 
                       order.getOrderId(), order.getTotals().getItems());

        } catch (Exception e) {
            logger.error("Failed to create order from paid cart {}: {}", 
                        order.getOrderId(), e.getMessage(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Find cart by business ID
     */
    private Cart findCartById(String cartId) {
        try {
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);
            
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);
            
            return cartWithMetadata != null ? cartWithMetadata.entity() : null;
        } catch (Exception e) {
            logger.error("Failed to find cart {}: {}", cartId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Snapshot cart data into order
     */
    private void snapshotCartToOrder(Cart cart, Order order) {
        // Convert cart lines to order lines
        List<Order.OrderLine> orderLines = new ArrayList<>();
        for (Cart.CartLine cartLine : cart.getLines()) {
            Order.OrderLine orderLine = new Order.OrderLine();
            orderLine.setSku(cartLine.getSku());
            orderLine.setName(cartLine.getName());
            orderLine.setUnitPrice(cartLine.getPrice());
            orderLine.setQty(cartLine.getQty());
            orderLine.setLineTotal(cartLine.getLineTotal());
            orderLines.add(orderLine);
        }
        order.setLines(orderLines);

        // Set totals
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(cart.getTotalItems());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);

        // Copy guest contact
        if (cart.getGuestContact() != null) {
            Order.GuestContact orderContact = new Order.GuestContact();
            orderContact.setName(cart.getGuestContact().getName());
            orderContact.setEmail(cart.getGuestContact().getEmail());
            orderContact.setPhone(cart.getGuestContact().getPhone());
            
            if (cart.getGuestContact().getAddress() != null) {
                Order.GuestAddress orderAddress = new Order.GuestAddress();
                orderAddress.setLine1(cart.getGuestContact().getAddress().getLine1());
                orderAddress.setCity(cart.getGuestContact().getAddress().getCity());
                orderAddress.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                orderAddress.setCountry(cart.getGuestContact().getAddress().getCountry());
                orderContact.setAddress(orderAddress);
            }
            
            order.setGuestContact(orderContact);
        }
    }

    /**
     * Decrement product inventory for ordered quantities
     */
    private void decrementProductInventory(List<Order.OrderLine> orderLines) {
        for (Order.OrderLine line : orderLines) {
            try {
                // Find product by SKU
                ModelSpec productModelSpec = new ModelSpec()
                        .withName(Product.ENTITY_NAME)
                        .withVersion(Product.ENTITY_VERSION);
                
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                        productModelSpec, line.getSku(), "sku", Product.class);
                
                if (productWithMetadata != null) {
                    Product product = productWithMetadata.entity();
                    int currentQty = product.getQuantityAvailable();
                    int newQty = Math.max(0, currentQty - line.getQty());
                    product.setQuantityAvailable(newQty);
                    
                    // Update product with manual transition
                    entityService.update(productWithMetadata.metadata().getId(), product, "update_product");
                    
                    logger.info("Decremented inventory for SKU {}: {} -> {}", 
                               line.getSku(), currentQty, newQty);
                } else {
                    logger.warn("Product not found for SKU: {}", line.getSku());
                }
            } catch (Exception e) {
                logger.error("Failed to decrement inventory for SKU {}: {}", 
                           line.getSku(), e.getMessage(), e);
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
            
            // Create shipment lines
            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            for (Order.OrderLine orderLine : order.getLines()) {
                Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
                shipmentLine.setSku(orderLine.getSku());
                shipmentLine.setQtyOrdered(orderLine.getQty());
                shipmentLine.setQtyPicked(0);
                shipmentLine.setQtyShipped(0);
                shipmentLines.add(shipmentLine);
            }
            shipment.setLines(shipmentLines);
            
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());
            
            // Create shipment entity
            entityService.create(shipment);
            
            logger.info("Created shipment {} for order {}", 
                       shipment.getShipmentId(), order.getOrderId());
        } catch (Exception e) {
            logger.error("Failed to create shipment for order {}: {}", 
                        order.getOrderId(), e.getMessage(), e);
        }
    }
}
