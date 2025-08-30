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

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@Component
public class PickCompleteProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PickCompleteProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PickCompleteProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
            // Only perform transition if currently in PICKING
            if ("PICKING".equalsIgnoreCase(shipment.getStatus())) {
                shipment.setStatus("WAITING_TO_SEND");
                shipment.setUpdatedAt(Instant.now().toString());
                logger.info("Shipment {} transitioned to WAITING_TO_SEND", shipment.getShipmentId());

                // Update related Order status.
                // Demo uses single shipment per order; find Order by orderId field.
                try {
                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.orderId", "EQUALS", shipment.getOrderId())
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
                            // Only move order to WAITING_TO_SEND when it's in a pre-shipping state
                            String current = order.getStatus();
                            if (current == null || "PICKING".equalsIgnoreCase(current) || "WAITING_TO_FULFILL".equalsIgnoreCase(current)) {
                                order.setStatus("WAITING_TO_SEND");
                                order.setUpdatedAt(Instant.now().toString());

                                String technicalId = null;
                                try {
                                    if (payload.getMeta() != null && payload.getMeta().get("entityId") != null) {
                                        technicalId = payload.getMeta().get("entityId").asText();
                                    }
                                } catch (Exception e) {
                                    logger.warn("Unable to extract technicalId from payload meta for order linked to shipment {}: {}", shipment.getShipmentId(), e.getMessage());
                                }

                                if (technicalId != null && !technicalId.isBlank()) {
                                    try {
                                        CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(technicalId), order);
                                        updated.get();
                                        logger.info("Updated Order {} status to WAITING_TO_SEND (technicalId={})", order.getOrderId(), technicalId);
                                    } catch (Exception e) {
                                        logger.error("Failed to update Order for shipment {}: {}", shipment.getShipmentId(), e.getMessage(), e);
                                    }
                                } else {
                                    logger.warn("No technicalId found for Order referenced by shipment {}. Skipping update.", shipment.getShipmentId());
                                }
                            } else {
                                logger.debug("Order {} current status '{}' does not require transition to WAITING_TO_SEND", order.getOrderId(), current);
                            }
                        }
                    } else {
                        logger.warn("No Order found for orderId {} referenced by shipment {}", shipment.getOrderId(), shipment.getShipmentId());
                    }
                } catch (Exception e) {
                    logger.error("Error while locating/updating Order for shipment {}: {}", shipment.getShipmentId(), e.getMessage(), e);
                }
            } else {
                logger.info("Shipment {} not in PICKING state (current: {}). No transition applied.", shipment.getShipmentId(), shipment.getStatus());
            }
        } catch (Exception e) {
            logger.error("Unexpected error processing shipment {}: {}", shipment != null ? shipment.getShipmentId() : "unknown", e.getMessage(), e);
            // Do not throw, return entity; serializer will handle errors via its handlers.
        }

        return shipment;
    }
}