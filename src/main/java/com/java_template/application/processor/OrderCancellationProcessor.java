package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Processor for cancelling orders.
 * Handles both cancel_order (placed → cancelled) and cancel_approved_order (approved → cancelled) transitions.
 */
@Component
public class OrderCancellationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCancellationProcessor.class);
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderCancellationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing order cancellation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .map(processingContext -> {
                Order order = processingContext.entity();
                
                logger.info("Cancelled order with ID: {} for pet ID: {}", order.getId(), order.getPetId());
                
                // Note: In a real implementation, this would:
                // 1. Release pet reservation by calling entityService.applyTransition() 
                //    on the pet entity with "cancel_reservation"
                // 2. Process refund if payment was made
                // 3. Send cancellation notification
                
                return order;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "OrderCancellationProcessor".equals(opSpec.operationName()) &&
               "Order".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
