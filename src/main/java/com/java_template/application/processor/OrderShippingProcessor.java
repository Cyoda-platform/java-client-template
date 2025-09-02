package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class OrderShippingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderShippingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OrderShippingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order shipping for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.isValid();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order entity = context.entity();

        // Validate shipping method is selected
        if (entity.getShippingMethod() == null || entity.getShippingMethod().trim().isEmpty()) {
            throw new IllegalStateException("Shipping method must be selected before shipping");
        }

        // Generate tracking number if not provided
        if (entity.getTrackingNumber() == null || entity.getTrackingNumber().trim().isEmpty()) {
            entity.setTrackingNumber("TRK" + System.currentTimeMillis());
        }

        // Set ship date
        entity.setShipDate(LocalDateTime.now());

        // Add shipping note
        entity.setNotes((entity.getNotes() != null ? entity.getNotes() + " " : "") + 
                       "Order shipped on " + LocalDateTime.now().toString() + 
                       " with tracking number: " + entity.getTrackingNumber());

        logger.info("Order shipped: {} with tracking: {}", entity.getId(), entity.getTrackingNumber());
        return entity;
    }
}
