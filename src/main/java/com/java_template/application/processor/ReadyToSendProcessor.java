package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ReadyToSendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReadyToSendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ReadyToSendProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Business logic:
        // - Transition shipment from PICKING -> WAITING_TO_SEND when ready
        // - Update shipment.updatedAt timestamp
        // - Also update associated Order status to WAITING_TO_SEND if applicable
        try {
            String currentStatus = shipment.getStatus();
            if (currentStatus != null && currentStatus.equalsIgnoreCase("PICKING")) {
                shipment.setStatus("WAITING_TO_SEND");
                shipment.setUpdatedAt(Instant.now().toString());

                // Update related Order status to WAITING_TO_SEND
                String orderId = shipment.getOrderId();
                if (orderId != null && !orderId.isBlank()) {
                    try {
                        UUID orderUuid = UUID.fromString(orderId);
                        CompletableFuture<DataPayload> itemFuture = entityService.getItem(orderUuid);
                        DataPayload payload = itemFuture.get();
                        if (payload != null && payload.getData() != null) {
                            Order order = objectMapper.treeToValue(payload.getData(), Order.class);
                            if (order != null) {
                                String orderStatus = order.getStatus();
                                // If order is in PICKING, advance to WAITING_TO_SEND; otherwise set if different to keep consistency
                                if (orderStatus == null || orderStatus.equalsIgnoreCase("PICKING") || !orderStatus.equalsIgnoreCase("WAITING_TO_SEND")) {
                                    order.setStatus("WAITING_TO_SEND");
                                    order.setUpdatedAt(Instant.now().toString());
                                    // Persist the updated order (allowed: updating other entities)
                                    try {
                                        entityService.updateItem(UUID.fromString(order.getOrderId()), order).get();
                                        logger.info("Updated Order {} status to WAITING_TO_SEND for Shipment {}", order.getOrderId(), shipment.getShipmentId());
                                    } catch (Exception ex) {
                                        logger.error("Failed to update Order {} for Shipment {}: {}", order.getOrderId(), shipment.getShipmentId(), ex.getMessage(), ex);
                                    }
                                }
                            }
                        } else {
                            logger.warn("No Order payload found for orderId {} referenced by Shipment {}", orderId, shipment.getShipmentId());
                        }
                    } catch (IllegalArgumentException iae) {
                        logger.error("Invalid orderId '{}' on Shipment {}: {}", orderId, shipment.getShipmentId(), iae.getMessage(), iae);
                    }
                } else {
                    logger.warn("Shipment {} has no orderId to update", shipment.getShipmentId());
                }
            } else {
                logger.debug("Shipment {} current status '{}' does not require READY_TO_SEND transition", shipment.getShipmentId(), currentStatus);
            }
        } catch (Exception e) {
            logger.error("Error processing Shipment {}: {}", shipment != null ? shipment.getShipmentId() : "unknown", e.getMessage(), e);
        }

        return shipment;
    }
}