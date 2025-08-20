package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.order.version_1.Order.OrderItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class AllocateInventoryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AllocateInventoryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AllocateInventoryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for inventory allocation request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.getItems() != null && !entity.getItems().isEmpty();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        if (order == null) return null;

        String status = order.getStatus();
        if ("INVENTORY_RESERVED".equalsIgnoreCase(status) || "INVENTORY_FAILED".equalsIgnoreCase(status)) {
            logger.info("Order already has inventory status: {}", status);
            return order;
        }

        // Use order items strongly typed
        List<OrderItem> items = order.getItems();
        if (items == null || items.isEmpty()) {
            order.setStatus("INVENTORY_FAILED");
            logger.warn("Order {} has no items to reserve", order.getOrderId());
            return order;
        }

        boolean anyReserved = false;
        boolean anyFailed = false;

        for (OrderItem it : items) {
            int qty = it.getQuantity() == null ? 0 : it.getQuantity();
            // simulate inventory check: randomize for demo
            int avail = ThreadLocalRandom.current().nextInt(0, 5);
            int reserved = Math.min(avail, qty);
            if (reserved > 0) anyReserved = true;
            if (reserved < qty) anyFailed = true;
            logger.debug("Inventory check sku={} requested={} available={} reserved={}", it.getSku(), qty, avail, reserved);
        }

        if (!anyReserved) {
            order.setStatus("INVENTORY_FAILED");
            logger.info("No inventory reserved for order {}. Marking INVENTORY_FAILED", order.getOrderId());
            return order;
        }

        // partial or full reservation
        order.setStatus("INVENTORY_RESERVED");
        logger.info("Inventory reserved for order {} (partial={}): moving to INVENTORY_RESERVED", order.getOrderId(), anyFailed);
        return order;
    }
}
