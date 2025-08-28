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

import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

@Component
public class AutoDeliverProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AutoDeliverProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AutoDeliverProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
            // Only auto-deliver shipments that are currently SENT
            String status = entity.getStatus();
            if (status == null) {
                logger.warn("Shipment {} has null status, skipping auto-deliver", entity.getId());
                return entity;
            }

            if (!"SENT".equalsIgnoreCase(status)) {
                logger.info("Shipment {} status is '{}', not eligible for auto-deliver", entity.getId(), status);
                return entity;
            }

            // Wait 5 seconds before moving to DELIVERED as per spec
            logger.info("Auto-deliver: waiting 5s before marking shipment {} as DELIVERED", entity.getId());
            try {
                Thread.sleep(5000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("Auto-deliver sleep interrupted for shipment {}: {}", entity.getId(), ie.getMessage());
            }

            // Update shipment status to DELIVERED
            entity.setStatus("DELIVERED");
            // Add delivered timestamp to trackingInfo map, preserving existing entries
            Map<String, Object> tracking = entity.getTrackingInfo();
            if (tracking == null) {
                tracking = new HashMap<>();
            } else {
                tracking = new HashMap<>(tracking);
            }
            tracking.put("deliveredAt", OffsetDateTime.now().toString());
            entity.setTrackingInfo(tracking);

            logger.info("Shipment {} marked as DELIVERED", entity.getId());

            // If this shipment is associated with an order, evaluate the order state.
            String orderId = entity.getOrderId();
            if (orderId != null && !orderId.isBlank()) {
                try {
                    CompletableFuture<DataPayload> orderFuture = entityService.getItem(UUID.fromString(orderId));
                    DataPayload orderPayload = orderFuture.get();
                    if (orderPayload == null || orderPayload.getData() == null) {
                        logger.warn("Order payload not found for orderId={} when processing shipment={}", orderId, entity.getId());
                    } else {
                        Order order = objectMapper.treeToValue(orderPayload.getData(), Order.class);

                        // Fetch all shipments for this order to verify if all are DELIVERED
                        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                                Condition.of("$.orderId", "EQUALS", order.getId())
                        );
                        CompletableFuture<List<DataPayload>> shipmentsFuture =
                                entityService.getItemsByCondition(Shipment.ENTITY_NAME, Shipment.ENTITY_VERSION, condition, true);
                        List<DataPayload> payloads = shipmentsFuture.get();

                        boolean allDelivered = true;
                        if (payloads == null || payloads.isEmpty()) {
                            logger.debug("No shipments found for order {} while evaluating delivered state.", order.getId());
                            allDelivered = false;
                        } else {
                            for (DataPayload sp : payloads) {
                                if (sp == null || sp.getData() == null) {
                                    allDelivered = false;
                                    break;
                                }
                                Shipment s = objectMapper.treeToValue(sp.getData(), Shipment.class);
                                String sStatus = s != null ? s.getStatus() : null;
                                if (sStatus == null || !"DELIVERED".equalsIgnoreCase(sStatus)) {
                                    allDelivered = false;
                                    break;
                                }
                            }
                        }

                        if (allDelivered) {
                            // Advance order status to DELIVERED if not already
                            if (order.getStatus() == null || !order.getStatus().equalsIgnoreCase("DELIVERED")) {
                                order.setStatus("DELIVERED");
                                try {
                                    CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(order.getId()), order);
                                    UUID updatedId = updated.get();
                                    logger.info("Order {} marked as DELIVERED after all shipments delivered (updated technicalId={})", order.getId(), updatedId);
                                } catch (Exception ue) {
                                    logger.error("Failed to update Order {} to DELIVERED: {}", order.getId(), ue.getMessage(), ue);
                                }
                            } else {
                                logger.debug("Order {} already in DELIVERED status", order.getId());
                            }
                        } else {
                            logger.debug("Order {} not all shipments DELIVERED yet", order.getId());
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error while evaluating/updating related Order for shipment {}: {}", entity.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception ex) {
            logger.error("Error while processing auto-deliver for shipment {}: {}", entity != null ? entity.getId() : "null", ex.getMessage(), ex);
            // Do not throw; return entity as-is so workflow can handle error surface if needed
        }

        return entity;
    }
}