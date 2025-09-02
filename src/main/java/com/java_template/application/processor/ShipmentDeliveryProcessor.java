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
public class ShipmentDeliveryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentDeliveryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ShipmentDeliveryProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment delivery for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract shipment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract shipment entity: " + error.getMessage());
            })
            .validate(this::isValidShipment, "Invalid shipment state")
            .map(this::processShipmentDelivery)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidShipment(Shipment shipment) {
        return shipment != null && shipment.isValid();
    }

    private Shipment processShipmentDelivery(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment shipment = context.entity();
        
        logger.info("Confirming delivery for shipment: {}", shipment.getShipmentId());

        // Update timestamp to reflect delivery confirmation
        shipment.updateTimestamp();

        try {
            // Update associated order state to DELIVERED
            EntityResponse<Order> orderResponse = entityService.findByBusinessId(Order.class, shipment.getOrderId());
            Order order = orderResponse.getData();
            
            if (order != null) {
                // The order state transition will be handled by the workflow
                logger.info("Associated order found for shipment delivery: orderId={}", shipment.getOrderId());
            } else {
                logger.warn("No order found for shipment: {}", shipment.getShipmentId());
            }

        } catch (Exception e) {
            logger.error("Failed to update associated order for shipment {}: {}", shipment.getShipmentId(), e.getMessage());
            // Don't fail the shipment transition if order update fails
        }

        logger.info("Shipment delivery confirmed successfully: shipmentId={}, orderId={}", 
            shipment.getShipmentId(), shipment.getOrderId());
        
        return shipment;
    }
}
