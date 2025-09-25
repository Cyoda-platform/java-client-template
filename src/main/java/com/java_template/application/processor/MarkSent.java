package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Processor to mark order and shipment as sent
 */
@Component
public class MarkSent implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkSent.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MarkSent(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processMarkSent)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Order> processMarkSent(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing mark sent for order: {}", order.getOrderId());

        try {
            // Update associated shipment status and quantities
            updateShipmentForSent(order.getOrderId());
            
            // Update order timestamp
            order.setUpdatedAt(LocalDateTime.now());
            
            logger.info("Order {} marked as sent", order.getOrderId());
            
        } catch (Exception e) {
            logger.error("Error processing mark sent for order: {}", order.getOrderId(), e);
            throw new RuntimeException("Failed to process mark sent", e);
        }

        return entityWithMetadata;
    }

    private void updateShipmentForSent(String orderId) {
        try {
            ModelSpec shipmentModelSpec = new ModelSpec()
                    .withName(Shipment.ENTITY_NAME)
                    .withVersion(Shipment.ENTITY_VERSION);

            // Find shipment by order ID
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.orderId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(orderId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            List<EntityWithMetadata<Shipment>> shipments = entityService.search(
                    shipmentModelSpec, groupCondition, Shipment.class);

            if (!shipments.isEmpty()) {
                EntityWithMetadata<Shipment> shipmentWithMetadata = shipments.get(0);
                Shipment shipment = shipmentWithMetadata.entity();
                
                // Update shipment status
                shipment.setStatus("SENT");
                shipment.setUpdatedAt(LocalDateTime.now());
                
                // Update shipment line quantities (mark all as shipped)
                if (shipment.getLines() != null) {
                    for (Shipment.ShipmentLine line : shipment.getLines()) {
                        line.setQtyPicked(line.getQtyOrdered());
                        line.setQtyShipped(line.getQtyOrdered());
                    }
                }
                
                entityService.update(shipmentWithMetadata.metadata().getId(), shipment, "mark_sent");
                
                logger.debug("Updated shipment {} status to SENT", shipment.getShipmentId());
            } else {
                logger.warn("No shipment found for order: {}", orderId);
            }
            
        } catch (Exception e) {
            logger.error("Error updating shipment for sent order: {}", orderId, e);
            throw new RuntimeException("Failed to update shipment for sent", e);
        }
    }
}
