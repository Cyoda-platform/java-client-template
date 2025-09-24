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
 * CreateOrderFromPaidProcessor - Creates order from paid cart
 * 
 * This processor:
 * 1. Snapshots cart lines and guest contact into Order
 * 2. Decrements Product.quantityAvailable for each ordered item
 * 3. Creates a single Shipment in PICKING status
 * 4. Generates short ULID for order number
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
        return order != null && order.isValid() && technicalId != null;
    }

    /**
     * Main business logic for creating order from paid cart
     */
    private EntityWithMetadata<Order> processOrderCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid cart for order: {}", order.getOrderId());

        try {
            // 1. Find the cart that was paid for (assuming orderId matches cartId for simplicity)
            Cart cart = findCartByOrderId(order.getOrderId());
            if (cart == null) {
                logger.error("Cart not found for order: {}", order.getOrderId());
                return entityWithMetadata;
            }

            // 2. Generate short ULID for order number (simplified - using UUID substring)
            String orderNumber = generateShortULID();
            order.setOrderNumber(orderNumber);

            // 3. Set initial status
            order.setStatus("WAITING_TO_FULFILL");

            // 4. Snapshot cart lines to order lines
            snapshotCartLinesToOrder(cart, order);

            // 5. Snapshot guest contact
            snapshotGuestContact(cart, order);

            // 6. Set timestamps
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            // 7. Decrement product quantities
            decrementProductQuantities(order.getLines());

            // 8. Create shipment
            createShipment(order);

            logger.info("Order {} created successfully with order number {}", 
                       order.getOrderId(), order.getOrderNumber());

        } catch (Exception e) {
            logger.error("Error creating order from paid cart: {}", order.getOrderId(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Find cart by order ID (simplified mapping)
     */
    private Cart findCartByOrderId(String orderId) {
        try {
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    cartModelSpec, orderId, "cartId", Cart.class);
            return cartResponse != null ? cartResponse.entity() : null;
        } catch (Exception e) {
            logger.error("Error finding cart for order: {}", orderId, e);
            return null;
        }
    }

    /**
     * Generate short ULID (simplified using UUID)
     */
    private String generateShortULID() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Snapshot cart lines to order lines
     */
    private void snapshotCartLinesToOrder(Cart cart, Order order) {
        List<Order.OrderLine> orderLines = new ArrayList<>();
        int totalItems = 0;
        double grandTotal = 0.0;

        if (cart.getLines() != null) {
            for (Cart.CartLine cartLine : cart.getLines()) {
                Order.OrderLine orderLine = new Order.OrderLine();
                orderLine.setSku(cartLine.getSku());
                orderLine.setName(cartLine.getName());
                orderLine.setUnitPrice(cartLine.getPrice());
                orderLine.setQty(cartLine.getQty());
                orderLine.setLineTotal(cartLine.getLineTotal());
                
                orderLines.add(orderLine);
                totalItems += cartLine.getQty();
                grandTotal += cartLine.getLineTotal();
            }
        }

        order.setLines(orderLines);
        
        Order.OrderTotals totals = new Order.OrderTotals();
        totals.setItems(totalItems);
        totals.setGrand(grandTotal);
        order.setTotals(totals);
    }

    /**
     * Snapshot guest contact from cart to order
     */
    private void snapshotGuestContact(Cart cart, Order order) {
        if (cart.getGuestContact() != null) {
            Order.OrderGuestContact orderContact = new Order.OrderGuestContact();
            orderContact.setName(cart.getGuestContact().getName());
            orderContact.setEmail(cart.getGuestContact().getEmail());
            orderContact.setPhone(cart.getGuestContact().getPhone());
            
            if (cart.getGuestContact().getAddress() != null) {
                Order.OrderGuestAddress orderAddress = new Order.OrderGuestAddress();
                orderAddress.setLine1(cart.getGuestContact().getAddress().getLine1());
                orderAddress.setLine2(cart.getGuestContact().getAddress().getLine2());
                orderAddress.setCity(cart.getGuestContact().getAddress().getCity());
                orderAddress.setState(cart.getGuestContact().getAddress().getState());
                orderAddress.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                orderAddress.setCountry(cart.getGuestContact().getAddress().getCountry());
                orderContact.setAddress(orderAddress);
            }
            
            order.setGuestContact(orderContact);
        }
    }

    /**
     * Decrement product quantities for ordered items
     */
    private void decrementProductQuantities(List<Order.OrderLine> orderLines) {
        if (orderLines == null) return;

        for (Order.OrderLine orderLine : orderLines) {
            try {
                // Find product by SKU
                ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
                EntityWithMetadata<Product> productResponse = entityService.findByBusinessId(
                        productModelSpec, orderLine.getSku(), "sku", Product.class);

                if (productResponse != null) {
                    Product product = productResponse.entity();
                    int currentQty = product.getQuantityAvailable();
                    int newQty = Math.max(0, currentQty - orderLine.getQty());
                    product.setQuantityAvailable(newQty);

                    // Update product with manual transition
                    entityService.update(productResponse.metadata().getId(), product, "update_product");
                    
                    logger.info("Decremented product {} quantity from {} to {}", 
                               orderLine.getSku(), currentQty, newQty);
                }
            } catch (Exception e) {
                logger.error("Error decrementing quantity for product: {}", orderLine.getSku(), e);
            }
        }
    }

    /**
     * Create shipment for the order
     */
    private void createShipment(Order order) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId(UUID.randomUUID().toString());
            shipment.setOrderId(order.getOrderId());
            shipment.setStatus("PICKING");
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());

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

            // Create shipment entity
            entityService.create(shipment);
            
            logger.info("Created shipment {} for order {}", shipment.getShipmentId(), order.getOrderId());
        } catch (Exception e) {
            logger.error("Error creating shipment for order: {}", order.getOrderId(), e);
        }
    }
}
