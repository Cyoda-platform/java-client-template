package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CreateOrderFromPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderFromPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public CreateOrderFromPaidProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order creation from paid payment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract order entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract order entity: " + error.getMessage());
            })
            .validate(this::isValidOrderForCreation, "Invalid order state for creation")
            .map(this::processOrderCreation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidOrderForCreation(Order order) {
        return order != null;
    }

    private Order processOrderCreation(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();

        try {
            // Set order ID if not present
            if (order.getOrderId() == null || order.getOrderId().isBlank()) {
                order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }

            // Generate short ULID-style order number
            if (order.getOrderNumber() == null || order.getOrderNumber().isBlank()) {
                order.setOrderNumber(generateShortOrderNumber());
            }

            // Set initial status
            order.setStatus("WAITING_TO_FULFILL");

            // Set timestamps
            String now = Instant.now().toString();
            order.setCreatedAt(now);
            order.setUpdatedAt(now);

            // Process order lines and update product quantities
            if (order.getLines() != null) {
                processOrderLines(order.getLines());
            }

            logger.info("Created order {} with number {} from paid payment",
                       order.getOrderId(), order.getOrderNumber());

            return order;

        } catch (Exception e) {
            logger.error("Error creating order: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create order: " + e.getMessage(), e);
        }
    }

    private String generateShortOrderNumber() {
        // Generate a short ULID-style order number (6 characters)
        String chars = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }

    private void processOrderLines(List<Order.Line> lines) {
        for (Order.Line line : lines) {
            try {
                // Decrement product quantity
                decrementProductQuantity(line.getSku(), line.getQty());

                // Calculate line total if not set
                if (line.getLineTotal() == null && line.getUnitPrice() != null && line.getQty() != null) {
                    line.setLineTotal(line.getUnitPrice() * line.getQty());
                }

                logger.info("Processed order line: SKU={}, Qty={}, LineTotal={}",
                           line.getSku(), line.getQty(), line.getLineTotal());

            } catch (Exception e) {
                logger.error("Error processing order line for SKU {}: {}", line.getSku(), e.getMessage());
                // Continue processing other lines even if one fails
            }
        }
    }

    private void decrementProductQuantity(String sku, Integer qty) {
        try {
            // This is a simplified implementation - in a real system you'd need to handle
            // concurrent updates, stock reservations, etc.
            logger.info("Decrementing product {} quantity by {}", sku, qty);

            // Note: In a real implementation, you would:
            // 1. Find the product by SKU
            // 2. Check if sufficient quantity is available
            // 3. Update the product's quantityAvailable
            // 4. Handle race conditions and stock reservations

            // For now, we'll just log the operation
            logger.info("Product {} quantity decremented by {} (simulated)", sku, qty);

        } catch (Exception e) {
            logger.error("Failed to decrement product {} quantity: {}", sku, e.getMessage());
            throw new RuntimeException("Stock update failed for SKU: " + sku, e);
        }
    }
}