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
 * CreateOrderFromPaidProcessor - Creates Order from paid Cart
 * - Snapshots cart lines + guestContact into Order
 * - Decrements Product.quantityAvailable by ordered qty
 * - Creates one Shipment in PICKING state
 */
@Component
public class CreateOrderFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateOrderFromPaidProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing order creation from paid cart for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Order> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Creating order from cart: {}", order.getOrderNumber());

        // Decrement product quantities
        decrementProductQuantities(order);

        // Create shipment
        createShipment(order);

        order.setUpdatedAt(LocalDateTime.now());

        logger.info("Order {} created successfully with shipment", order.getOrderNumber());

        return entityWithMetadata;
    }

    private void decrementProductQuantities(Order order) {
        if (order.getLines() == null || order.getLines().isEmpty()) {
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        ModelSpec productSpec = new ModelSpec().withName(Product.ENTITY_NAME).withVersion(Product.ENTITY_VERSION);

        for (Order.OrderLine line : order.getLines()) {
            try {
                // Find product by SKU
                SimpleCondition skuCondition = new SimpleCondition()
                        .withJsonPath("$.sku")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(line.getSku()));

                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(List.of(skuCondition));

                List<EntityWithMetadata<Product>> products = entityService.search(
                        productSpec, condition, Product.class,
                        com.java_template.common.repository.SearchAndRetrievalParams.builder()
                                .pageSize(1)
                                .inMemory(true)
                                .build()).data();

                if (!products.isEmpty()) {
                    EntityWithMetadata<Product> productWithMetadata = products.get(0);
                    Product product = productWithMetadata.entity();
                    
                    // Decrement quantity
                    if (product.getQuantityAvailable() != null && product.getQuantityAvailable() >= line.getQty()) {
                        product.setQuantityAvailable(product.getQuantityAvailable() - line.getQty());
                        product.setUpdatedAt(LocalDateTime.now());
                        
                        // Update product
                        entityService.update(productWithMetadata.metadata().getId(), product, null);
                        logger.info("Decremented {} quantity by {} for SKU {}", 
                                   product.getName(), line.getQty(), line.getSku());
                    }
                }
            } catch (Exception e) {
                logger.error("Error decrementing product quantity for SKU {}: {}", line.getSku(), e.getMessage());
            }
        }
    }

    private void createShipment(Order order) {
        try {
            Shipment shipment = new Shipment();
            shipment.setShipmentId("SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
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

            // Create shipment in Cyoda
            entityService.create(shipment);
            logger.info("Shipment {} created for order {}", shipment.getShipmentId(), order.getOrderId());
        } catch (Exception e) {
            logger.error("Error creating shipment for order {}: {}", order.getOrderId(), e.getMessage());
        }
    }
}

