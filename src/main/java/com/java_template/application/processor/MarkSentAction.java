package com.java_template.application.processor;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.shipment.version_1.Shipment.ShipmentLine;
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

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class MarkSentAction implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkSentAction.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public MarkSentAction(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
            // Only allow transition from WAITING_TO_SEND -> SENT per workflow rules
            String currentStatus = shipment.getStatus();
            if (currentStatus == null || !currentStatus.equalsIgnoreCase("WAITING_TO_SEND")) {
                logger.info("Shipment {} is not in WAITING_TO_SEND state (current: {}). Skipping MarkSentAction.", shipment.getShipmentId(), currentStatus);
                return shipment;
            }
        } catch (Exception e) {
            logger.warn("Error while checking shipment status for shipment {}: {}", shipment != null ? shipment.getShipmentId() : "unknown", e.getMessage(), e);
            // proceed cautiously
        }

        // Mark the shipment as SENT and update timestamp
        try {
            shipment.setStatus("SENT");
        } catch (Exception ex) {
            logger.warn("Unable to set shipment status to SENT: {}", ex.getMessage(), ex);
        }
        try {
            shipment.setUpdatedAt(Instant.now().toString());
        } catch (Exception ex) {
            logger.debug("Unable to set shipment updatedAt: {}", ex.getMessage());
        }

        // Ensure qtyShipped is set (prefer qtyPicked, fallback to qtyOrdered)
        List<ShipmentLine> lines = shipment.getLines();
        if (lines != null) {
            for (ShipmentLine line : lines) {
                if (line == null) continue;
                try {
                    Integer qtyOrdered = line.getQtyOrdered();
                    Integer qtyPicked = line.getQtyPicked();
                    Integer qtyShipped = line.getQtyShipped();

                    Integer toShip = null;
                    if (qtyPicked != null && qtyPicked >= 0) {
                        toShip = qtyPicked;
                    } else if (qtyOrdered != null && qtyOrdered >= 0) {
                        toShip = qtyOrdered;
                    }

                    if (toShip != null) {
                        // Do not ship more than ordered if ordered is known
                        if (qtyOrdered != null && toShip > qtyOrdered) {
                            toShip = qtyOrdered;
                        }
                        if (qtyShipped == null || !qtyShipped.equals(toShip)) {
                            line.setQtyShipped(toShip);
                        }
                    } else {
                        // if nothing to infer, ensure qtyShipped at least 0
                        if (qtyShipped == null) {
                            line.setQtyShipped(0);
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to adjust shipment line qtyShipped for sku {}: {}", line.getSku(), ex.getMessage(), ex);
                }
            }
        }

        // Update related Order status to SENT (single shipment per order in demo)
        String orderRef = shipment.getOrderId();
        if (orderRef != null && !orderRef.isBlank()) {
            try {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.orderId", "EQUALS", orderRef)
                );
                CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                    Order.ENTITY_NAME,
                    Order.ENTITY_VERSION,
                    condition,
                    true
                );

                List<DataPayload> dataPayloads = itemsFuture.get();
                if (dataPayloads != null && !dataPayloads.isEmpty()) {
                    DataPayload payload = dataPayloads.get(0);
                    Order order = objectMapper.treeToValue(payload.getData(), Order.class);

                    if (order != null) {
                        // Only update if not already SENT or DELIVERED
                        String orderStatus = order.getStatus();
                        if (orderStatus == null || !orderStatus.equalsIgnoreCase("SENT")) {
                            order.setStatus("SENT");
                        }
                        order.setUpdatedAt(Instant.now().toString());

                        // Extract technical id for update
                        String technicalId = null;
                        if (payload.getMeta() != null && payload.getMeta().has("entityId")) {
                            technicalId = payload.getMeta().get("entityId").asText();
                        }

                        if (technicalId != null && !technicalId.isBlank()) {
                            try {
                                CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(technicalId), order);
                                updated.get(); // block to ensure update executed
                                logger.info("Updated Order (orderId={}) status to SENT (technicalId={})", orderRef, technicalId);
                            } catch (InterruptedException | ExecutionException e) {
                                logger.error("Failed to update Order entity for orderId {}: {}", orderRef, e.getMessage(), e);
                            }
                        } else {
                            logger.warn("Could not determine technicalId for Order with orderId={}", orderRef);
                        }
                    } else {
                        logger.warn("Order payload could not be mapped for orderId={}", orderRef);
                    }
                } else {
                    logger.warn("No Order found with orderId={}", orderRef);
                }
            } catch (InterruptedException | ExecutionException ie) {
                logger.error("Error while querying Order for orderId={}: {}", orderRef, ie.getMessage(), ie);
            } catch (Exception ex) {
                logger.error("Unexpected error while updating Order for orderId={}: {}", orderRef, ex.getMessage(), ex);
            }
        } else {
            logger.warn("Shipment {} has no orderId reference, skipping order update", shipment.getShipmentId());
        }

        return shipment;
    }
}