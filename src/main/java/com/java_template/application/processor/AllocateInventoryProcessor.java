package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
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

import java.util.*;
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

        // Simple idempotent reservation simulation
        List<?> items = order.getItems();
        boolean anyReserved = false;
        boolean anyFailed = false;
        List<Map<String,Object>> reservationDetails = new ArrayList<>();
        for (Object o : items) {
            if (!(o instanceof Map)) continue;
            Map<?,?> m = (Map<?,?>) o;
            String sku = m.get("sku") == null ? "" : m.get("sku").toString();
            int qty = 0;
            try { qty = Integer.parseInt(String.valueOf(m.get("quantity"))); } catch (Exception ignored) {}

            // simulate inventory check: randomize for demo
            int avail = ThreadLocalRandom.current().nextInt(0, 5);
            Map<String,Object> line = new HashMap<>();
            line.put("sku", sku);
            line.put("requested", qty);
            line.put("reserved", Math.min(avail, qty));
            reservationDetails.add(line);
            if (Math.min(avail, qty) > 0) anyReserved = true;
            if (Math.min(avail, qty) < qty) anyFailed = true;
        }

        if (!anyReserved) {
            order.setStatus("INVENTORY_FAILED");
            try {
                Map<String,Object> meta = order.getMetadata() == null || !(order.getMetadata() instanceof Map) ? new HashMap<>() : (Map<String,Object>) order.getMetadata();
                meta.put("reservationDetails", reservationDetails);
                order.setMetadata(meta);
            } catch (Exception ignored) {}
            logger.info("No inventory reserved for order {}. Marking INVENTORY_FAILED", order.getOrderId());
            return order;
        }

        // partial or full reservation
        order.setStatus("INVENTORY_RESERVED");
        try {
            Map<String,Object> meta = order.getMetadata() == null || !(order.getMetadata() instanceof Map) ? new HashMap<>() : (Map<String,Object>) order.getMetadata();
            meta.put("reservationDetails", reservationDetails);
            meta.put("reservationToken", UUID.randomUUID().toString());
            order.setMetadata(meta);
        } catch (Exception ignored) {}

        logger.info("Inventory reserved for order {}. Details: {}", order.getOrderId(), reservationDetails);
        return order;
    }
}
