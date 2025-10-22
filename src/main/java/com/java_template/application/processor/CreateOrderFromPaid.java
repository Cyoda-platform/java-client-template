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
 * ABOUTME: Processor that creates an order from a paid payment by:
 * 1. Snapshotting cart lines and guest contact into Order
 * 2. Decrementing Product.quantityAvailable for each ordered item
 * 3. Creating a single Shipment in PICKING state
 */
@Component
public class CreateOrderFromPaid implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaid.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateOrderFromPaid(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::processBusinessLogic)
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
        Order entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic: create order from paid payment
     */
    private EntityWithMetadata<Order> processBusinessLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid payment: {}", order.getOrderId());

        // Extract cartId from order (should be passed in orderId or a separate field)
        // For now, we'll assume the order has a reference to the cart
        // In a real scenario, this would be passed as a parameter
        String cartId = order.getOrderId().startsWith("CART-") ? order.getOrderId() : null;

        if (cartId != null) {
            // Fetch the cart
            ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            EntityWithMetadata<Cart> cartResponse = entityService.findByBusinessId(
                    cartModelSpec, cartId, "cartId", Cart.class);

            if (cartResponse != null) {
                Cart cart = cartResponse.entity();

                // Snapshot cart lines into order
                if (cart.getLines() != null && !cart.getLines().isEmpty()) {
                    List<Order.OrderLine> orderLines = new ArrayList<>();
                    for (Cart.CartLine cartLine : cart.getLines()) {
                        Order.OrderLine orderLine = new Order.OrderLine();
                        orderLine.setSku(cartLine.getSku());
                        orderLine.setName(cartLine.getName());
                        orderLine.setUnitPrice(cartLine.getPrice());
                        orderLine.setQty(cartLine.getQty());
                        orderLine.setLineTotal(cartLine.getPrice() * cartLine.getQty());
                        orderLines.add(orderLine);

                        // Decrement product quantity
                        decrementProductQuantity(cartLine.getSku(), cartLine.getQty());
                    }
                    order.setLines(orderLines);

                    // Set order totals
                    Order.OrderTotals totals = new Order.OrderTotals();
                    totals.setItems(cart.getTotalItems());
                    totals.setGrand(cart.getGrandTotal());
                    order.setTotals(totals);
                }

                // Snapshot guest contact
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
            }
        }

        // Create shipment
        createShipment(order);

        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        logger.info("Order created from paid payment: {} with order number: {}", order.getOrderId(), order.getOrderNumber());

        return entityWithMetadata;
    }

    /**
     * Decrement product quantity available
     */
    private void decrementProductQuantity(String sku, Integer qty) {
        try {
            ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> productResponse = entityService.findByBusinessId(
                    productModelSpec, sku, "sku", Product.class);

            if (productResponse != null) {
                Product product = productResponse.entity();
                if (product.getQuantityAvailable() != null) {
                    product.setQuantityAvailable(product.getQuantityAvailable() - qty);
                    entityService.update(productResponse.metadata().getId(), product, "decrement_quantity");
                    logger.debug("Product {} quantity decremented by {}", sku, qty);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to decrement product quantity for SKU {}: {}", sku, e.getMessage());
        }
    }

    /**
     * Create a single shipment for the order
     */
    private void createShipment(Order order) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId("SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            shipment.setOrderId(order.getOrderId());
            shipment.setStatus("PICKING");

            // Create shipment lines from order lines
            if (order.getLines() != null && !order.getLines().isEmpty()) {
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
            }

            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());

            entityService.create(shipment);
            logger.info("Shipment created for order: {}", order.getOrderId());
        } catch (Exception e) {
            logger.warn("Failed to create shipment for order {}: {}", order.getOrderId(), e.getMessage());
        }
    }
}

