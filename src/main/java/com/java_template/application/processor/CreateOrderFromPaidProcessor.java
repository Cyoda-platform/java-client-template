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
 * Processor to create order from paid payment
 * Snapshots cart data, decrements product stock, creates shipment
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
        logger.info("Processing order creation from paid payment for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::createOrderFromPaid)
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
     * Creates order from paid payment, decrements stock, creates shipment
     */
    private EntityWithMetadata<Order> createOrderFromPaid(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid payment: {}", order.getOrderId());

        // Set order status
        order.setStatus("WAITING_TO_FULFILL");
        
        // Set timestamps
        if (order.getCreatedAt() == null) {
            order.setCreatedAt(LocalDateTime.now());
        }
        order.setUpdatedAt(LocalDateTime.now());

        // Decrement product stock for each order line
        decrementProductStock(order);

        // Create shipment for this order
        createShipment(order);

        logger.info("Order {} created successfully with {} lines", 
                   order.getOrderId(), order.getLines() != null ? order.getLines().size() : 0);

        return entityWithMetadata;
    }

    /**
     * Decrements product stock for each order line
     */
    private void decrementProductStock(Order order) {
        if (order.getLines() == null) {
            return;
        }

        ModelSpec productModelSpec = new ModelSpec()
                .withName(Product.ENTITY_NAME)
                .withVersion(Product.ENTITY_VERSION);

        for (Order.OrderLine line : order.getLines()) {
            try {
                // Find product by SKU
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                        productModelSpec, line.getSku(), "sku", Product.class);

                if (productWithMetadata != null) {
                    Product product = productWithMetadata.entity();
                    
                    // Decrement quantity available
                    int currentQty = product.getQuantityAvailable() != null ? product.getQuantityAvailable() : 0;
                    int newQty = Math.max(0, currentQty - line.getQty());
                    product.setQuantityAvailable(newQty);

                    // Update product
                    entityService.update(productWithMetadata.metadata().getId(), product, "update_product");
                    
                    logger.info("Decremented stock for SKU {}: {} -> {}", 
                               line.getSku(), currentQty, newQty);
                } else {
                    logger.warn("Product not found for SKU: {}", line.getSku());
                }
            } catch (Exception e) {
                logger.error("Failed to decrement stock for SKU: {}", line.getSku(), e);
            }
        }
    }

    /**
     * Creates a shipment for the order
     */
    private void createShipment(Order order) {
        try {
            // Create shipment entity
            Shipment shipment = new Shipment();
            shipment.setShipmentId("SHIP-" + UUID.randomUUID().toString().substring(0, 8));
            shipment.setOrderId(order.getOrderId());
            shipment.setStatus("PICKING");
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());

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

            // Save shipment
            EntityWithMetadata<Shipment> shipmentWithMetadata = entityService.create(shipment);
            
            logger.info("Created shipment {} for order {}", 
                       shipment.getShipmentId(), order.getOrderId());
        } catch (Exception e) {
            logger.error("Failed to create shipment for order: {}", order.getOrderId(), e);
        }
    }
}
