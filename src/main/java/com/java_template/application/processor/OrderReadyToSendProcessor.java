package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderReadyToSendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderReadyToSendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderReadyToSendProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order ready to send for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidOrder, "Invalid order state")
            .map(this::processOrderReadyToSend)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidOrder(Order order) {
        return order != null && order.isValid();
    }

    private Order processOrderReadyToSend(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();

        // Validate order is in PICKING state
        String currentState = context.request().getPayload().getMeta().get("state").toString();
        if (!"PICKING".equals(currentState)) {
            throw new IllegalStateException("Order must be in PICKING state to mark ready to send");
        }

        // Get associated shipment by orderId
        Optional<EntityResponse<Shipment>> shipmentResponse = getShipmentByOrderId(order.getOrderId());
        if (shipmentResponse.isEmpty()) {
            throw new IllegalArgumentException("No shipment found for order: " + order.getOrderId());
        }

        Shipment shipment = shipmentResponse.get().getData();
        UUID shipmentId = shipmentResponse.get().getMetadata().getId();

        // Trigger shipment READY_TO_SEND transition
        entityService.update(shipmentId, shipment, "READY_TO_SEND");

        // Set updatedAt timestamp
        order.setUpdatedAt(Instant.now());

        logger.info("Order {} ready to send", order.getOrderId());
        return order;
    }

    private Optional<EntityResponse<Shipment>> getShipmentByOrderId(String orderId) {
        Condition condition = Condition.of("$.orderId", "EQUALS", orderId);
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
        return entityService.getFirstItemByCondition(Shipment.class, Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION, searchCondition, true);
    }
}
