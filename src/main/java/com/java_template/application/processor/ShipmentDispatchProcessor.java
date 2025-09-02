package com.java_template.application.processor;

import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ShipmentDispatchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentDispatchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ShipmentDispatchProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment dispatch for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract shipment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract shipment entity: " + error.getMessage());
            })
            .validate(this::isValidShipment, "Invalid shipment state")
            .map(this::processShipmentDispatch)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidShipment(Shipment shipment) {
        return shipment != null && shipment.isValid();
    }

    private Shipment processShipmentDispatch(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();
        
        logger.info("Dispatching shipment: {}", shipment.getShipmentId());

        // Update shipment.lines qtyShipped = qtyPicked
        shipment.markAllItemsShipped();

        // Update timestamp
        shipment.updateTimestamp();

        try {
            // Update associated order state to SENT
            EntityResponse<Order> orderResponse = entityService.findByBusinessId(Order.class, shipment.getOrderId());
            Order order = orderResponse.getData();
            
            if (order != null) {
                // The order state transition will be handled by the workflow
                logger.info("Associated order found for shipment dispatch: orderId={}", shipment.getOrderId());
            } else {
                logger.warn("No order found for shipment: {}", shipment.getShipmentId());
            }

        } catch (Exception e) {
            logger.error("Failed to update associated order for shipment {}: {}", shipment.getShipmentId(), e.getMessage());
            // Don't fail the shipment transition if order update fails
        }

        logger.info("Shipment dispatched successfully: shipmentId={}, orderId={}", 
            shipment.getShipmentId(), shipment.getOrderId());
        
        return shipment;
    }
}
