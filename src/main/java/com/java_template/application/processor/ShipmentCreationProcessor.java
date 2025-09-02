package com.java_template.application.processor;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ShipmentCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ShipmentCreationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidShipment, "Invalid shipment state")
            .map(this::processShipmentCreation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidShipment(Shipment shipment) {
        return shipment != null;
    }

    private Shipment processShipmentCreation(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();

        // Validate shipment has orderId
        if (shipment.getOrderId() == null || shipment.getOrderId().trim().isEmpty()) {
            throw new IllegalArgumentException("Shipment must have a valid order ID");
        }

        // Validate order exists and is in WAITING_TO_FULFILL state
        Optional<EntityResponse<Order>> orderResponse = getOrderById(shipment.getOrderId());
        if (orderResponse.isEmpty()) {
            throw new IllegalArgumentException("Order not found: " + shipment.getOrderId());
        }

        Order order = orderResponse.get().getData();
        String orderState = orderResponse.get().getMetadata().getState();

        if (!"WAITING_TO_FULFILL".equals(orderState)) {
            throw new IllegalArgumentException("Order must be in WAITING_TO_FULFILL state, current state: " + orderState);
        }

        // Create shipment lines from order lines
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

        // Set timestamps
        Instant now = Instant.now();
        shipment.setCreatedAt(now);
        shipment.setUpdatedAt(now);

        logger.info("Shipment {} created for order {}", shipment.getShipmentId(), shipment.getOrderId());
        return shipment;
    }

    private Optional<EntityResponse<Order>> getOrderById(String orderId) {
        Condition condition = Condition.of("$.orderId", "EQUALS", orderId);
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
        return entityService.getFirstItemByCondition(Order.class, Order.ENTITY_NAME, Order.ENTITY_VERSION, searchCondition, true);
    }
}
