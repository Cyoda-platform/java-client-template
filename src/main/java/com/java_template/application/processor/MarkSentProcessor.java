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
 * MarkSentProcessor - Marks order and shipment as sent
 * 
 * Updates the associated shipment status to SENT and marks
 * shipment lines as fully shipped when order moves to SENT.
 */
@Component
public class MarkSentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkSentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MarkSentProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid() && technicalId != null;
    }

    /**
     * Main business logic for marking order/shipment as sent
     */
    private EntityWithMetadata<Order> processMarkSent(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing mark sent for order: {}", order.getOrderId());

        try {
            // Update order timestamp
            order.setUpdatedAt(LocalDateTime.now());

            // Find and update associated shipment
            updateShipmentAsSent(order.getOrderId());

            logger.info("Order {} marked as sent", order.getOrderId());

        } catch (Exception e) {
            logger.error("Error processing mark sent for order: {}", order.getOrderId(), e);
        }

        return entityWithMetadata;
    }

    /**
     * Update shipment as sent for the order
     */
    private void updateShipmentAsSent(String orderId) {
        try {
            // Find shipment by order ID
            ModelSpec shipmentModelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.orderId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(orderId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            List<EntityWithMetadata<Shipment>> shipments = entityService.search(shipmentModelSpec, groupCondition, Shipment.class);

            if (!shipments.isEmpty()) {
                EntityWithMetadata<Shipment> shipmentResponse = shipments.get(0);
                Shipment shipment = shipmentResponse.entity();
                shipment.setStatus("SENT");
                shipment.setUpdatedAt(LocalDateTime.now());

                // Mark all lines as fully shipped
                if (shipment.getLines() != null) {
                    for (Shipment.ShipmentLine line : shipment.getLines()) {
                        line.setQtyPicked(line.getQtyOrdered());
                        line.setQtyShipped(line.getQtyOrdered());
                    }
                }

                // Update shipment with manual transition
                entityService.update(shipmentResponse.metadata().getId(), shipment, "mark_sent");
                
                logger.info("Updated shipment {} status to SENT", shipment.getShipmentId());
            }
        } catch (Exception e) {
            logger.error("Error updating shipment as sent for order: {}", orderId, e);
        }
    }
}
