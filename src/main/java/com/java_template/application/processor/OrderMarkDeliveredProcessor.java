```java
package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
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
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class OrderMarkDeliveredProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderMarkDeliveredProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    @Autowired
    private ObjectMapper objectMapper;

    public OrderMarkDeliveredProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order mark delivered for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.isValid();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();

        try {
            // Update order timestamp
            order.setUpdatedAt(Instant.now());

            // Trigger shipment delivered transition
            triggerShipmentMarkDelivered(order.getOrderId());

            logger.info("Order {} marked as delivered", order.getOrderId());

            return order;

        } catch (Exception e) {
            logger.error("Error processing order mark delivered: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process order mark delivered: " + e.getMessage(), e);
        }
    }

    private void triggerShipmentMarkDelivered(String orderId) {
        try {
            CompletableFuture<Shipment> shipmentFuture = getShipmentByOrderId(orderId);
            Shipment shipment = shipmentFuture.join();

            if (shipment != null) {
                UUID shipmentEntityId = getShipmentEntityId(shipment.getShipmentId()).join();
                if (shipmentEntityId != null) {
                    entityService.applyTransition(shipmentEntityId, "MARK_DELIVERED").join();
                    logger.info("Applied MARK_DELIVERED transition to shipment {} for order {}",
                        shipment.getShipmentId(), orderId);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to trigger shipment mark delivered for order {}: {}", orderId, e.getMessage(), e);
        }
    }

    private CompletableFuture<Shipment> getShipmentByOrderId(String orderId) {
        Condition orderIdCondition = Condition.of("$.orderId", "EQUALS", orderId);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", orderIdCondition);

        return entityService.getFirstItemByCondition(
            Shipment.ENTITY_NAME,
            Shipment.ENTITY_VERSION,
            condition,
            true
        ).thenApply(optionalPayload -> {
            if (optionalPayload.isPresent()) {
                try {
                    return objectMapper.convertValue(optionalPayload.get().getData(), Shipment.class);
                } catch (Exception e) {
                    logger.error("Error converting shipment data: {}", e.getMessage(), e);
                    return null;
                }
            }
            return null;
        });
    }

    private CompletableFuture<UUID> getShipmentEntityId(String shipmentId) {
        Condition shipmentIdCondition = Condition.of("$.shipmentId", "EQUALS", shipmentId);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", shipmentIdCondition);

        return entityService.getFirstItemByCondition(
            Shipment.ENTITY_NAME,
            Shipment.ENTITY_VERSION,
            condition,
            true
        ).thenApply(optionalPayload -> {
            if (optionalPayload.isPresent()) {
                return optionalPayload.get().getData().getId();
            }
            return null;
        });
    }
}
```