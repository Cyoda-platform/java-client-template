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
 * ABOUTME: Processor that creates an order from a paid cart, snapshots cart data into order,
 * decrements product stock, and creates a shipment for order fulfillment.
 */
@Component
public class CreateOrderFromPaid implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaid.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateOrderFromPaid(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

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

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        return entityWithMetadata != null && 
               entityWithMetadata.entity() != null && 
               entityWithMetadata.entity().isValid();
    }

    private EntityWithMetadata<Order> createOrderFromPaidCart(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();
        
        logger.debug("Creating order from paid cart for order: {}", order.getOrderId());
        
        try {
            // Find the cart associated with this order (assuming cartId is stored in orderId for now)
            // In a real implementation, you'd have a proper relationship
            Cart cart = findCartForOrder(order);
            
            if (cart != null) {
                // Snapshot cart data into order
                snapshotCartToOrder(cart, order);
                
                // Decrement product stock
                decrementProductStock(cart);
                
                // Create shipment
                createShipmentForOrder(order);
                
                // Set order status and timestamps
                order.setStatus("WAITING_TO_FULFILL");
                order.setCreatedAt(LocalDateTime.now());
                order.setUpdatedAt(LocalDateTime.now());
                
                logger.info("Order {} created successfully from cart", order.getOrderId());
            } else {
                logger.error("Cart not found for order: {}", order.getOrderId());
            }
            
        } catch (Exception e) {
            logger.error("Error creating order from paid cart: {}", order.getOrderId(), e);
        }
        
        return entityWithMetadata;
    }

    private Cart findCartForOrder(Order order) {
        // This is a simplified implementation - in reality you'd have proper relationships
        // For now, assume the orderId contains the cartId or there's a way to find it
        try {
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);
            
            ObjectMapper objectMapper = new ObjectMapper();
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.status")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree("CONVERTED"));
            
            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));
            
            List<EntityWithMetadata<Cart>> carts = entityService.search(cartModelSpec, groupCondition, Cart.class);
            
            // Return the first converted cart (simplified logic)
            if (!carts.isEmpty()) {
                return carts.get(0).entity();
            }
        } catch (Exception e) {
            logger.error("Error finding cart for order: {}", order.getOrderId(), e);
        }
        
        return null;
    }

    private void snapshotCartToOrder(Cart cart, Order order) {
        // Convert cart lines to order lines
        List<Order.OrderLine> orderLines = new ArrayList<>();
        
        if (cart.getLines() != null) {
            for (Cart.CartLine cartLine : cart.getLines()) {
                Order.OrderLine orderLine = new Order.OrderLine();
                orderLine.setSku(cartLine.getSku());
                orderLine.setName(cartLine.getName());
                orderLine.setUnitPrice(cartLine.getPrice());
                orderLine.setQty(cartLine.getQty());
                orderLine.setLineTotal(cartLine.getPrice() * cartLine.getQty());
                orderLines.add(orderLine);
            }
        }
        
        order.setLines(orderLines);
        
        // Set totals
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(cart.getTotalItems());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);
        
        // Copy guest contact
        if (cart.getGuestContact() != null) {
            Order.OrderGuestContact orderContact = new Order.OrderGuestContact();
            orderContact.setName(cart.getGuestContact().getName());
            orderContact.setEmail(cart.getGuestContact().getEmail());
            orderContact.setPhone(cart.getGuestContact().getPhone());
            
            if (cart.getGuestContact().getAddress() != null) {
                Order.OrderAddress orderAddress = new Order.OrderAddress();
                orderAddress.setLine1(cart.getGuestContact().getAddress().getLine1());
                orderAddress.setCity(cart.getGuestContact().getAddress().getCity());
                orderAddress.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                orderAddress.setCountry(cart.getGuestContact().getAddress().getCountry());
                orderContact.setAddress(orderAddress);
            }
            
            order.setGuestContact(orderContact);
        }
        
        logger.debug("Cart data snapshotted to order: {}", order.getOrderId());
    }

    private void decrementProductStock(Cart cart) {
        if (cart.getLines() == null) return;
        
        ModelSpec productModelSpec = new ModelSpec()
                .withName(Product.ENTITY_NAME)
                .withVersion(Product.ENTITY_VERSION);
        
        for (Cart.CartLine line : cart.getLines()) {
            try {
                // Find product by SKU
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                        productModelSpec, line.getSku(), "sku", Product.class);
                
                if (productWithMetadata != null) {
                    Product product = productWithMetadata.entity();
                    
                    // Decrement quantity available
                    int newQuantity = product.getQuantityAvailable() - line.getQty();
                    product.setQuantityAvailable(Math.max(0, newQuantity)); // Don't go below 0
                    
                    // Update product
                    entityService.update(productWithMetadata.metadata().getId(), product, "update_product");
                    
                    logger.debug("Decremented stock for SKU {}: {} -> {}", 
                               line.getSku(), product.getQuantityAvailable() + line.getQty(), product.getQuantityAvailable());
                }
            } catch (Exception e) {
                logger.error("Error decrementing stock for SKU: {}", line.getSku(), e);
            }
        }
    }

    private void createShipmentForOrder(Order order) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId(UUID.randomUUID().toString());
            shipment.setOrderId(order.getOrderId());
            shipment.setStatus("PICKING");
            
            // Convert order lines to shipment lines
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
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());
            
            // Create shipment entity
            ModelSpec shipmentModelSpec = new ModelSpec()
                    .withName(Shipment.ENTITY_NAME)
                    .withVersion(Shipment.ENTITY_VERSION);
            
            entityService.create(shipment);
            
            logger.info("Shipment {} created for order {}", shipment.getShipmentId(), order.getOrderId());
            
        } catch (Exception e) {
            logger.error("Error creating shipment for order: {}", order.getOrderId(), e);
        }
    }
}
