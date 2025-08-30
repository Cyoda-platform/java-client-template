package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
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
                logger.info("Shipment {} transitioned to WAITING_TO_SEND", shipment.getShipmentId());

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
                                // If order is in PICKING or not yet WAITING_TO_SEND, advance it
                                if (orderStatus == null || orderStatus.equalsIgnoreCase("PICKING") || !orderStatus.equalsIgnoreCase("WAITING_TO_SEND")) {
                                    order.setStatus("WAITING_TO_SEND");
                                    order.setUpdatedAt(Instant.now().toString());

                                    // Determine technical id from payload meta (preferred) or fallback to order.getOrderId()
                                    String technicalId = null;
                                    try {
                                        JsonNode meta = payload.getMeta();
                                        if (meta != null && meta.has("entityId")) {
                                            technicalId = meta.get("entityId").asText();
                                        }
                                    } catch (Exception me) {
                                        logger.debug("Unable to extract technicalId from payload meta for order referenced by shipment {}: {}", shipment.getShipmentId(), me.getMessage());
                                    }

                                    try {
                                        if (technicalId != null && !technicalId.isBlank()) {
                                            entityService.updateItem(UUID.fromString(technicalId), order).get();
                                        } else {
                                            // Fallback: try using order.getOrderId() if it's a UUID string
                                            try {
                                                UUID fallback = UUID.fromString(order.getOrderId());
                                                entityService.updateItem(fallback, order).get();
                                            } catch (Exception ex) {
                                                logger.warn("No valid technical id available to update Order referenced by Shipment {}. Order not updated.", shipment.getShipmentId());
                                            }
                                        }
                                        logger.info("Updated Order status to WAITING_TO_SEND for Shipment {}", shipment.getShipmentId());
                                    } catch (Exception ex) {
                                        logger.error("Failed to update Order for Shipment {}: {}", shipment.getShipmentId(), ex.getMessage(), ex);
                                    }
                                } else {
                                    logger.debug("Order {} already in WAITING_TO_SEND or later state", order.getOrderId());
                                }
                            } else {
                                logger.warn("Order deserialized to null for orderId {} while processing Shipment {}", orderId, shipment.getShipmentId());
                            }
                        } else {
                            logger.warn("No Order payload found for orderId {} referenced by Shipment {}", orderId, shipment.getShipmentId());
                        }
                    } catch (IllegalArgumentException iae) {
                        logger.error("Invalid orderId '{}' on Shipment {}: {}", orderId, shipment.getShipmentId(), iae.getMessage(), iae);
                    } catch (Exception e) {
                        logger.error("Error while loading/updating Order for Shipment {}: {}", shipment.getShipmentId(), e.getMessage(), e);
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