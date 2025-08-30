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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class MarkSentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkSentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public MarkSentProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        Shipment entity = context.entity();

        // Mark shipment as SENT
        try {
            entity.setStatus("SENT");
        } catch (Exception e) {
            logger.error("Failed to set shipment status to SENT for shipmentId {}: {}", entity.getShipmentId(), e.getMessage(), e);
            return entity;
        }

        // For demo: Single shipment per order. Update the related Order status to SENT.
        String orderId = entity.getOrderId();
        if (orderId != null && !orderId.isBlank()) {
            try {
                // orderId is stored as serialized UUID (technical id). Use it directly to fetch the Order payload.
                UUID orderUuid = UUID.fromString(orderId);
                CompletableFuture<DataPayload> orderFuture = entityService.getItem(orderUuid);
                DataPayload payload = orderFuture.get();
                if (payload != null && payload.getData() != null) {
                    Order order = objectMapper.treeToValue(payload.getData(), Order.class);
                    if (order != null) {
                        order.setStatus("SENT");
                        // If shipment has an updatedAt timestamp, propagate it to the order for consistency
                        if (entity.getUpdatedAt() != null && !entity.getUpdatedAt().isBlank()) {
                            try {
                                order.setUpdatedAt(entity.getUpdatedAt());
                            } catch (Exception ignore) {
                                logger.debug("Unable to set updatedAt on order {}: {}", orderId, ignore.getMessage());
                            }
                        }
                        // Persist updated order using the technical id (orderUuid)
                        entityService.updateItem(orderUuid, order).get();
                        logger.info("Updated Order {} status to SENT for Shipment {}", orderUuid.toString(), entity.getShipmentId());
                    } else {
                        logger.warn("Order deserialized to null for orderId {} while processing Shipment {}", orderId, entity.getShipmentId());
                    }
                } else {
                    logger.warn("No Order payload found for orderId {} while processing Shipment {}", orderId, entity.getShipmentId());
                }
            } catch (IllegalArgumentException iae) {
                logger.warn("Invalid orderId '{}' on Shipment {}: {}", orderId, entity.getShipmentId(), iae.getMessage());
            } catch (Exception ex) {
                logger.error("Failed to update Order status for orderId {}: {}", orderId, ex.getMessage(), ex);
            }
        } else {
            logger.warn("Shipment {} has no orderId; skipping Order update", entity.getShipmentId());
        }

        return entity;
    }
}