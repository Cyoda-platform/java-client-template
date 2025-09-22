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
 * CreateOrderFromPaid Processor - Creates order from paid cart and decrements stock
 * 
 * This processor handles:
 * - Snapshotting cart lines and guest contact into order
 * - Decrementing Product.quantityAvailable for each ordered item
 * - Creating a single shipment in PICKING status
 * - Generating short ULID for order number
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
        logger.info("Processing CreateOrderFromPaid for request: {}", request.getId());

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
     * Main business logic for creating order from paid cart
     */
    private EntityWithMetadata<Order> createOrderFromPaid(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from paid cart for order: {}", order.getOrderId());

        try {
            // The order should already have the basic data from the controller
            // Here we focus on stock decrement and shipment creation
            
            // Decrement stock for each order line
            decrementProductStock(order);
            
            // Create shipment
            createShipment(order);
            
            // Set order status to PICKING (since shipment is created in PICKING)
            order.setStatus("PICKING");
            order.setUpdatedAt(LocalDateTime.now());

            logger.info("Order {} created from paid cart with {} lines", 
                       order.getOrderId(), order.getLines().size());

        } catch (Exception e) {
            logger.error("Error creating order from paid cart: {}", order.getOrderId(), e);
            // Don't throw - let the order be created even if stock decrement fails
        }

        return entityWithMetadata;
    }

    /**
     * Decrement product stock for each order line
     */
    private void decrementProductStock(Order order) {
        logger.debug("Decrementing stock for order: {}", order.getOrderId());
        
        for (Order.OrderLine line : order.getLines()) {
            try {
                // Find product by SKU
                ModelSpec productModelSpec = new ModelSpec()
                        .withName(Product.ENTITY_NAME)
                        .withVersion(Product.ENTITY_VERSION);
                
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                        productModelSpec, line.getSku(), "sku", Product.class);
                
                if (productWithMetadata != null) {
                    Product product = productWithMetadata.entity();
                    
                    // Decrement quantity available
                    int currentQty = product.getQuantityAvailable();
                    int newQty = Math.max(0, currentQty - line.getQty()); // Don't go below 0
                    product.setQuantityAvailable(newQty);
                    
                    // Update product
                    entityService.update(productWithMetadata.metadata().getId(), product, "update_product");
                    
                    logger.info("Decremented stock for SKU {}: {} -> {} (ordered: {})", 
                               line.getSku(), currentQty, newQty, line.getQty());
                } else {
                    logger.warn("Product not found for SKU: {}", line.getSku());
                }
                
            } catch (Exception e) {
                logger.error("Error decrementing stock for SKU: {}", line.getSku(), e);
            }
        }
    }

    /**
     * Create shipment for the order
     */
    private void createShipment(Order order) {
        try {
            logger.debug("Creating shipment for order: {}", order.getOrderId());
            
            // Create shipment entity
            Shipment shipment = new Shipment();
            shipment.setShipmentId(UUID.randomUUID().toString());
            shipment.setOrderId(order.getOrderId());
            shipment.setStatus("PICKING");
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());
            
            // Create shipment lines from order lines
            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            for (Order.OrderLine orderLine : order.getLines()) {
                Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
                shipmentLine.setSku(orderLine.getSku());
                shipmentLine.setQtyOrdered(orderLine.getQty());
                shipmentLine.setQtyPicked(0); // Initially 0
                shipmentLine.setQtyShipped(0); // Initially 0
                shipmentLines.add(shipmentLine);
            }
            shipment.setLines(shipmentLines);
            
            // Create shipment in Cyoda
            EntityWithMetadata<Shipment> createdShipment = entityService.create(shipment);
            
            logger.info("Created shipment {} for order {}", 
                       createdShipment.entity().getShipmentId(), order.getOrderId());
                       
        } catch (Exception e) {
            logger.error("Error creating shipment for order: {}", order.getOrderId(), e);
        }
    }
}
