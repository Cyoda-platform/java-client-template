package com.java_template.application.processor;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class MarkDeliveredAction implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkDeliveredAction.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public MarkDeliveredAction(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Shipment entity) {
        return entity != null && entity.isValid();
    }

    private Shipment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();

        try {
            // Only proceed to mark delivered if current state is SENT (case-insensitive)
            String currentStatus = shipment.getStatus();
            if (currentStatus == null || !currentStatus.equalsIgnoreCase("SENT")) {
                logger.info("Shipment {} not in SENT state (current: {}), skipping MarkDeliveredAction.", shipment.getShipmentId(), currentStatus);
                return shipment;
            }

            // Update shipment status to DELIVERED and set updatedAt
            shipment.setStatus("DELIVERED");
            shipment.setUpdatedAt(Instant.now().toString());
            logger.info("Shipment {} marked as DELIVERED", shipment.getShipmentId());

            // For demo: single shipment per order. Update associated Order to DELIVERED.
            String orderId = shipment.getOrderId();
            if (orderId == null || orderId.isBlank()) {
                logger.warn("Shipment {} has no orderId, cannot update Order status.", shipment.getShipmentId());
                return shipment;
            }

            try {
                UUID orderUuid = UUID.fromString(orderId);

                // Retrieve Order by technical UUID
                CompletableFuture<DataPayload> payloadFuture = entityService.getItem(orderUuid);
                DataPayload payload = payloadFuture.get();
                if (payload == null || payload.getData() == null) {
                    logger.warn("Order entity not found for id: {}", orderId);
                    return shipment;
                }
                Order order = objectMapper.treeToValue(payload.getData(), Order.class);
                if (order == null) {
                    logger.warn("Failed to deserialize Order for id: {}", orderId);
                    return shipment;
                }

                if (order.getStatus() == null || !order.getStatus().equalsIgnoreCase("DELIVERED")) {
                    order.setStatus("DELIVERED");
                    order.setUpdatedAt(Instant.now().toString());
                    // Update the Order entity in Cyoda using the same technical UUID
                    CompletableFuture<java.util.UUID> updateFuture = entityService.updateItem(orderUuid, order);
                    updateFuture.get();
                    logger.info("Order {} marked as DELIVERED", orderUuid);
                } else {
                    logger.info("Order {} already in DELIVERED state", orderId);
                }
            } catch (IllegalArgumentException iae) {
                logger.error("Invalid Order UUID in shipment.orderId: {}", orderId, iae);
            } catch (Exception ex) {
                logger.error("Failed to update Order status for orderId {}: {}", orderId, ex.getMessage(), ex);
            }

        } catch (Exception e) {
            logger.error("Error processing MarkDeliveredAction for shipment {}: {}", shipment != null ? shipment.getShipmentId() : "unknown", e.getMessage(), e);
        }

        return shipment;
    }
}