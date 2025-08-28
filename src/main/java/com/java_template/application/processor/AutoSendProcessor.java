package com.java_template.application.processor;
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
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.order.version_1.Order;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * AutoSendProcessor - moves Shipment from WAITING_TO_SEND -> SENT after a short delay.
 * Also evaluates related Order: if all Shipments for the Order are SENT, it updates Order.status -> SENT.
 */
@Component
public class AutoSendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AutoSendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AutoSendProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        try {
            // Only act when shipment is in WAITING_TO_SEND
            if (entity.getStatus() != null && entity.getStatus().equalsIgnoreCase("WAITING_TO_SEND")) {
                logger.info("AutoSendProcessor triggered for shipment id={}, shipmentNumber={}", entity.getId(), entity.getShipmentNumber());

                // Wait 3 seconds to simulate dispatch delay
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("Sleep interrupted in AutoSendProcessor for shipment id={}", entity.getId());
                }

                // Update shipment status to SENT and add tracking info
                entity.setStatus("SENT");
                Map<String, Object> tracking = entity.getTrackingInfo() != null ? new HashMap<>(entity.getTrackingInfo()) : new HashMap<>();
                tracking.put("sentAt", Instant.now().toString());
                tracking.put("carrier", "AUTO");
                if (entity.getShipmentNumber() != null) {
                    tracking.put("trackingNumber", entity.getShipmentNumber());
                }
                entity.setTrackingInfo(tracking);

                logger.info("Shipment id={} marked as SENT", entity.getId());

                // Attempt to update related Order if all shipments are SENT
                if (entity.getOrderId() != null && !entity.getOrderId().isBlank()) {
                    try {
                        // Retrieve all shipments for this order
                        SearchConditionRequest condShipments = SearchConditionRequest.group(
                                "AND",
                                Condition.of("$.orderId", "EQUALS", entity.getOrderId())
                        );
                        CompletableFuture<List<DataPayload>> shipmentsFuture = entityService.getItemsByCondition(
                                Shipment.ENTITY_NAME,
                                Shipment.ENTITY_VERSION,
                                condShipments,
                                true
                        );
                        List<DataPayload> shipmentPayloads = shipmentsFuture.get();
                        boolean allSent = true;
                        if (shipmentPayloads != null) {
                            for (DataPayload payload : shipmentPayloads) {
                                if (payload != null && payload.getData() != null) {
                                    try {
                                        Shipment s = objectMapper.treeToValue(payload.getData(), Shipment.class);
                                        if (s != null && (s.getStatus() == null || !s.getStatus().equalsIgnoreCase("SENT"))) {
                                            allSent = false;
                                            break;
                                        }
                                    } catch (Exception ex) {
                                        logger.warn("Failed to convert shipment payload while evaluating order shipments: {}", ex.getMessage());
                                        allSent = false;
                                        break;
                                    }
                                } else {
                                    allSent = false;
                                    break;
                                }
                            }
                        } else {
                            allSent = false;
                        }

                        if (allSent) {
                            // Load the order entity
                            SearchConditionRequest condOrder = SearchConditionRequest.group(
                                    "AND",
                                    Condition.of("$.id", "EQUALS", entity.getOrderId())
                            );
                            CompletableFuture<List<DataPayload>> ordersFuture = entityService.getItemsByCondition(
                                    Order.ENTITY_NAME,
                                    Order.ENTITY_VERSION,
                                    condOrder,
                                    true
                            );
                            List<DataPayload> orderPayloads = ordersFuture.get();
                            if (orderPayloads != null && !orderPayloads.isEmpty()) {
                                DataPayload orderPayload = orderPayloads.get(0);
                                Order order = objectMapper.treeToValue(orderPayload.getData(), Order.class);
                                if (order != null) {
                                    // Only update if order is in WAITING_TO_SEND (or similar)
                                    if (order.getStatus() != null && order.getStatus().equalsIgnoreCase("WAITING_TO_SEND")) {
                                        order.setStatus("SENT");
                                        try {
                                            CompletableFuture<java.util.UUID> updated = entityService.updateItem(
                                                    java.util.UUID.fromString(order.getId()),
                                                    order
                                            );
                                            updated.get();
                                            logger.info("Order id={} marked as SENT after all shipments sent", order.getId());
                                        } catch (Exception e) {
                                            logger.error("Failed to update Order id={} to SENT: {}", order.getId(), e.getMessage(), e);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error while evaluating/updating related Order for shipment id={}: {}", entity.getId(), e.getMessage(), e);
                    }
                }
            } else {
                logger.debug("AutoSendProcessor ignored shipment id={} with status={}", entity.getId(), entity.getStatus());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in AutoSendProcessor for shipment id={}: {}", entity != null ? entity.getId() : "null", ex.getMessage(), ex);
        }

        return entity;
    }
}