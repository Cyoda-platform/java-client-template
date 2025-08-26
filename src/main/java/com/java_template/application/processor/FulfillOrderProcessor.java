package com.java_template.application.processor;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class FulfillOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FulfillOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FulfillOrderProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state or not in Confirmed state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        // Entity must be present, valid and in CONFIRMED state to proceed with fulfillment
        return entity != null && entity.isValid() && "Confirmed".equalsIgnoreCase(entity.getStatus());
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order entity = context.entity();

        // Only perform fulfillment transition. This processor marks a confirmed order as shipped.
        // Do not perform persistence calls on this Order entity directly; Cyoda will persist changes.
        if (entity == null) {
            logger.warn("Received null Order in processing context");
            return entity;
        }

        String currentStatus = entity.getStatus();
        if (!"Confirmed".equalsIgnoreCase(currentStatus)) {
            logger.warn("Order {} is not in Confirmed state (current: {}), skipping fulfillment", entity.getOrderId(), currentStatus);
            return entity;
        }

        // Transition order to Shipped
        entity.setStatus("Shipped");
        logger.info("Order {} status changed from Confirmed to Shipped", entity.getOrderId());

        // Note: Stock reservation and payment validation are assumed to have been handled during validation/confirmation.
        // If additional side-effects (like updating Product inventory) are required, use EntityService to operate on other entities.
        return entity;
    }
}