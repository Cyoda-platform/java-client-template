package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ShipmentTriggerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ShipmentTriggerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ShipmentTriggerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing shipment trigger for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid order for shipment trigger")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order order) {
        return order != null && order.getId() != null;
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        try {
            Shipment shipment = new Shipment();
            shipment.setId(UUID.randomUUID());
            shipment.setOrderId(order.getId());
            shipment.setProvider("SIMULATED_SHIPPER");
            shipment.setStatus("CREATED");
            shipment.setTrackingNumber("TRK-" + UUID.randomUUID().toString());
            logger.info("Triggered shipment {} for order {}", shipment.getId(), order.getId());
            // Real implementation would persist and possibly call external provider.
        } catch (Exception e) {
            logger.error("Error triggering shipment for order {}: {}", order != null ? order.getId() : "<null>", e.getMessage());
        }
        return order;
    }
}
