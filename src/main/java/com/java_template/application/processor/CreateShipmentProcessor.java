package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
public class CreateShipmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateShipmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateShipmentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for CreateShipment request: {}", request.getId());

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
        return entity != null && entity.getOrderId() != null && entity.getItems() != null && !entity.getItems().isEmpty();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        if (order == null) return null;

        String status = order.getStatus();
        if ("FULFILLMENT_CREATED".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)) {
            logger.info("Order already has fulfillment state: {}", status);
            return order;
        }

        // Build shipments based on reservation details if present
        List<?> items = order.getItems();
        List<Map<String,Object>> reservation = null;
        try {
            Object meta = order.getMetadata();
            if (meta instanceof Map) {
                Object rd = ((Map<?,?>) meta).get("reservationDetails");
                if (rd instanceof List) reservation = (List<Map<String,Object>>) rd;
            }
        } catch (Exception ignored) {}

        List<Shipment> created = new ArrayList<>();
        if (reservation != null && !reservation.isEmpty()) {
            for (Map<String,Object> line : reservation) {
                Integer reserved = line.get("reserved") == null ? 0 : Integer.parseInt(String.valueOf(line.get("reserved")));
                if (reserved <= 0) continue;
                Shipment s = new Shipment();
                s.setOrderId(order.getOrderId());
                s.setItems(java.util.Collections.singletonList(java.util.Collections.singletonMap("sku", line.get("sku"))));
                s.setStatus("READY");
                s.setShipmentId("SHP-" + UUID.randomUUID().toString());
                created.add(s);
            }
        } else {
            // fallback: create a single shipment for all items
            Shipment s = new Shipment();
            s.setOrderId(order.getOrderId());
            s.setItems(items);
            s.setStatus("READY");
            s.setShipmentId("SHP-" + UUID.randomUUID().toString());
            created.add(s);
        }

        // Persist shipments
        for (Shipment s : created) {
            try {
                CompletableFuture<java.util.UUID> f = entityService.addItem(
                    Shipment.ENTITY_NAME,
                    String.valueOf(Shipment.ENTITY_VERSION),
                    s
                );
                java.util.UUID id = f.get();
                logger.info("Created shipment technicalId={} for order {}", id, order.getOrderId());
            } catch (Exception e) {
                logger.warn("Failed to create shipment for order {}: {}", order.getOrderId(), e.getMessage());
            }
        }

        order.setStatus("FULFILLMENT_CREATED");
        return order;
    }
}
