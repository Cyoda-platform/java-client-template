package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.shipment.version_1.Shipment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.dto.EntityWithMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Order Mark Delivered Processor
 * 
 * Updates shipment when order is delivered.
 * Transitions: MARK_DELIVERED
 */
@Component
public class OrderMarkDeliveredProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderMarkDeliveredProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderMarkDeliveredProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing order mark delivered for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Order.class)
            .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
            .map(this::processOrderMarkDelivered)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        return order != null && order.isValid() && entityWithMetadata.getId() != null;
    }

    private EntityWithMetadata<Order> processOrderMarkDelivered(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing order mark delivered: {}", order.getOrderId());

        try {
            // Get shipment by orderId
            ModelSpec shipmentModelSpec = new ModelSpec().withName(Shipment.ENTITY_NAME).withVersion(Shipment.ENTITY_VERSION);
            EntityWithMetadata<Shipment> shipmentResponse = entityService.findByBusinessId(
                shipmentModelSpec, order.getOrderId(), "orderId", Shipment.class);
            
            if (shipmentResponse == null) {
                throw new IllegalStateException("Shipment not found for order: " + order.getOrderId());
            }
            
            Shipment shipment = shipmentResponse.entity();
            shipment.setUpdatedAt(LocalDateTime.now());
            
            // Update shipment with transition
            entityService.update(shipmentResponse.getId(), shipment, "MARK_DELIVERED");
            
            // Update order timestamp
            order.setUpdatedAt(LocalDateTime.now());

            logger.info("Order mark delivered processed: {}", order.getOrderId());

            return entityWithMetadata;
        } catch (Exception e) {
            logger.error("Error processing order mark delivered", e);
            throw new RuntimeException("Failed to process order mark delivered: " + e.getMessage(), e);
        }
    }
}
