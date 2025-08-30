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
public class MarkDeliveredProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkDeliveredProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public MarkDeliveredProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Business logic:
        // 1. Mark the shipment status as DELIVERED.
        // 2. Ensure each shipment line has qtyShipped equal to qtyOrdered if not already set.
        // 3. Update the associated Order status to DELIVERED (single shipment demo).
        try {
            // 1. Mark shipment delivered
            entity.setStatus("DELIVERED");

            // 2. Ensure shipped quantities reflect ordered quantities
            if (entity.getLines() != null) {
                for (Shipment.ShipmentLine line : entity.getLines()) {
                    if (line != null) {
                        Integer ordered = line.getQtyOrdered() != null ? line.getQtyOrdered() : 0;
                        Integer shipped = line.getQtyShipped();
                        if (shipped == null || shipped < ordered) {
                            line.setQtyShipped(ordered);
                        }
                    }
                }
            }

            // 3. Update associated Order status to DELIVERED
            String orderIdStr = entity.getOrderId();
            if (orderIdStr != null && !orderIdStr.isBlank()) {
                try {
                    UUID orderUuid = UUID.fromString(orderIdStr);
                    CompletableFuture<DataPayload> itemFuture = entityService.getItem(orderUuid);
                    DataPayload payload = itemFuture.get();
                    if (payload != null && payload.getData() != null) {
                        Order order = objectMapper.treeToValue(payload.getData(), Order.class);
                        if (order != null) {
                            order.setStatus("DELIVERED");
                            // propagate an updatedAt timestamp if present on shipment, otherwise leave as-is
                            if (entity.getUpdatedAt() != null && !entity.getUpdatedAt().isBlank()) {
                                order.setUpdatedAt(entity.getUpdatedAt());
                            }
                            // Update other entities via EntityService (allowed)
                            entityService.updateItem(orderUuid, order).get();
                        } else {
                            logger.warn("Order payload could not be mapped to Order class for orderId: {}", orderIdStr);
                        }
                    } else {
                        logger.warn("No payload found for Order with id: {}", orderIdStr);
                    }
                } catch (IllegalArgumentException iae) {
                    logger.warn("Order id is not a valid UUID, skipping order update: {}", orderIdStr);
                }
            } else {
                logger.warn("Shipment has no orderId, skipping order update. shipmentId={}", entity.getShipmentId());
            }

        } catch (Exception e) {
            // Keep processor resilient: log and rethrow as runtime to let serializer error handler capture it
            logger.error("Failed to process MarkDelivered logic for shipment {}: {}", entity != null ? entity.getShipmentId() : "null", e.getMessage(), e);
            throw new RuntimeException(e);
        }

        return entity;
    }
}