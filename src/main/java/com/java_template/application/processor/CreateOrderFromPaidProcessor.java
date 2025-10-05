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
 * ABOUTME: Processor that creates an order from a paid cart, decrements product
 * inventory, and creates a shipment for order fulfillment.
 */
@Component
public class CreateOrderFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateOrderFromPaidProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::processOrderCreationLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Order> processOrderCreationLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.info("Creating order from paid cart for order: {}", order.getOrderId());

        try {
            // 1. Decrement product inventory for each order line
            decrementProductInventory(order);

            // 2. Create shipment for the order
            createShipmentForOrder(order);

            // 3. Update order timestamps
            order.setUpdatedAt(LocalDateTime.now());

            logger.info("Order {} created successfully with {} lines", 
                       order.getOrderId(), order.getLines().size());

        } catch (Exception e) {
            logger.error("Error processing order creation for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create order from paid cart", e);
        }

        return entityWithMetadata;
    }

    private void decrementProductInventory(Order order) {
        logger.debug("Decrementing inventory for order: {}", order.getOrderId());

        ModelSpec productModelSpec = new ModelSpec()
                .withName(Product.ENTITY_NAME)
                .withVersion(Product.ENTITY_VERSION);

        for (Order.OrderLine orderLine : order.getLines()) {
            try {
                // Find product by SKU
                SimpleCondition skuCondition = new SimpleCondition()
                        .withJsonPath("$.sku")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(orderLine.getSku()));

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(skuCondition));

                List<EntityWithMetadata<Product>> products = entityService.search(
                        productModelSpec, condition, Product.class);

                if (products.isEmpty()) {
                    logger.warn("Product not found for SKU: {}", orderLine.getSku());
                    continue;
                }

                EntityWithMetadata<Product> productWithMetadata = products.get(0);
                Product product = productWithMetadata.entity();

                // Decrement quantity available
                int currentQty = product.getQuantityAvailable();
                int orderedQty = orderLine.getQty();
                int newQty = Math.max(0, currentQty - orderedQty);

                product.setQuantityAvailable(newQty);

                // Update product with manual transition
                entityService.update(productWithMetadata.metadata().getId(), product, "update_inventory");

                logger.debug("Decremented inventory for SKU {}: {} -> {} (ordered: {})",
                           orderLine.getSku(), currentQty, newQty, orderedQty);

            } catch (Exception e) {
                logger.error("Error decrementing inventory for SKU: {}", orderLine.getSku(), e);
                // Continue processing other items even if one fails
            }
        }
    }

    private void createShipmentForOrder(Order order) {
        logger.debug("Creating shipment for order: {}", order.getOrderId());

        try {
            // Create shipment entity
            Shipment shipment = new Shipment();
            shipment.setShipmentId(UUID.randomUUID().toString());
            shipment.setOrderId(order.getOrderId());
            shipment.setCreatedAt(LocalDateTime.now());
            shipment.setUpdatedAt(LocalDateTime.now());

            // Convert order lines to shipment lines
            List<Shipment.ShipmentLine> shipmentLines = new ArrayList<>();
            for (Order.OrderLine orderLine : order.getLines()) {
                Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
                shipmentLine.setSku(orderLine.getSku());
                shipmentLine.setQtyOrdered(orderLine.getQty());
                shipmentLine.setQtyPicked(0); // Initially not picked
                shipmentLine.setQtyShipped(0); // Initially not shipped
                shipmentLines.add(shipmentLine);
            }
            shipment.setLines(shipmentLines);

            // Create shipment entity in Cyoda
            EntityWithMetadata<Shipment> createdShipment = entityService.create(shipment);

            logger.info("Created shipment {} for order {}", 
                       createdShipment.entity().getShipmentId(), order.getOrderId());

        } catch (Exception e) {
            logger.error("Error creating shipment for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to create shipment", e);
        }
    }
}
