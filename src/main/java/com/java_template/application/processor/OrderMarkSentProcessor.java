package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class OrderMarkSentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderMarkSentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderMarkSentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order mark sent for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract order entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract order entity: " + error.getMessage());
            })
            .validate(this::isValidOrder, "Invalid order state")
            .map(this::processMarkSent)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidOrder(Order order) {
        return order != null && order.isValid();
    }

    private Order processMarkSent(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        
        logger.info("Marking order as sent: {}", order.getOrderId());

        try {
            // Find associated shipment by orderId
            SearchConditionRequest condition = new SearchConditionRequest();
            List<Condition> conditions = new ArrayList<>();
            conditions.add(Condition.of("orderId", "equals", order.getOrderId()));
            condition.setConditions(conditions);

            Optional<EntityResponse<Shipment>> shipmentResponseOpt = entityService.getFirstItemByCondition(
                Shipment.class, condition, false);
            
            if (shipmentResponseOpt.isPresent()) {
                Shipment shipment = shipmentResponseOpt.get().getData();
                
                // Update shipment lines with shipped quantities (mark all as shipped)
                shipment.markAllItemsShipped();
                
                // Update shipment to SENT state via transition
                entityService.update(shipmentResponseOpt.get().getId(), shipment, "CONFIRM_DELIVERY");
                
                logger.info("Updated shipment to SENT state: shipmentId={}", shipment.getShipmentId());
            } else {
                logger.warn("No shipment found for order: {}", order.getOrderId());
            }

        } catch (Exception e) {
            logger.error("Failed to update shipment for order {}: {}", order.getOrderId(), e.getMessage());
            // Don't fail the order transition if shipment update fails
        }

        // Update order timestamp
        order.updateTimestamp();

        logger.info("Order marked as sent: orderId={}", order.getOrderId());
        
        return order;
    }
}
