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
 * ABOUTME: Processor that creates an Order from a paid Payment, snapshots cart lines,
 * decrements product stock, and creates a Shipment.
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

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Order> processBusinessLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid payment: {}", order.getOrderId());

        // Fetch the cart to get line items and guest contact
        ModelSpec cartModelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
        EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                cartModelSpec, order.getOrderId(), "cartId", Cart.class);

        if (cartWithMetadata == null) {
            logger.warn("Cart not found for order: {}", order.getOrderId());
            return entityWithMetadata;
        }

        Cart cart = cartWithMetadata.entity();

        // Snapshot cart lines into order lines and decrement product stock
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

                // Decrement product stock
                decrementProductStock(cartLine.getSku(), cartLine.getQty());
            }
        }
        order.setLines(orderLines);

        // Copy guest contact from cart
        if (cart.getGuestContact() != null) {
            Order.GuestContact orderContact = new Order.GuestContact();
            orderContact.setName(cart.getGuestContact().getName());
            orderContact.setEmail(cart.getGuestContact().getEmail());
            orderContact.setPhone(cart.getGuestContact().getPhone());
            if (cart.getGuestContact().getAddress() != null) {
                Order.Address orderAddress = new Order.Address();
                orderAddress.setLine1(cart.getGuestContact().getAddress().getLine1());
                orderAddress.setCity(cart.getGuestContact().getAddress().getCity());
                orderAddress.setPostcode(cart.getGuestContact().getAddress().getPostcode());
                orderAddress.setCountry(cart.getGuestContact().getAddress().getCountry());
                orderContact.setAddress(orderAddress);
            }
            order.setGuestContact(orderContact);
        }

        // Set order totals
        Order.Totals totals = new Order.Totals();
        totals.setItems(cart.getTotalItems());
        totals.setGrand(cart.getGrandTotal());
        order.setTotals(totals);

        // Set timestamps
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // Create shipment
        createShipment(order);

        logger.info("Order {} created from paid payment with {} items", order.getOrderId(), orderLines.size());

        return entityWithMetadata;
    }

    private void decrementProductStock(String sku, Integer qty) {
        try {
            ModelSpec productModelSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);
            EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                    productModelSpec, sku, "sku", Product.class);

            if (productWithMetadata != null) {
                Product product = productWithMetadata.entity();
                if (product.getQuantityAvailable() != null) {
                    product.setQuantityAvailable(product.getQuantityAvailable() - qty);
                    entityService.update(productWithMetadata.metadata().getId(), product, null);
                    logger.debug("Product {} stock decremented by {}", sku, qty);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to decrement stock for product {}: {}", sku, e.getMessage());
        }
    }

    private void createShipment(Order order) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId(UUID.randomUUID().toString());
            shipment.setOrderId(order.getOrderId());
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

            // Create shipment in Cyoda
            entityService.create(shipment);
            logger.info("Shipment {} created for order {}", shipment.getShipmentId(), order.getOrderId());
        } catch (Exception e) {
            logger.warn("Failed to create shipment for order {}: {}", order.getOrderId(), e.getMessage());
        }
    }
}

