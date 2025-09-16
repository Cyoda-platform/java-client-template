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
 * Processor for approving orders.
 * Handles the approve_order transition from placed to approved.
 */
@Component
public class OrderApprovalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderApprovalProcessor.class);
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderApprovalProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing order approval for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .map(processingContext -> {
                Order order = processingContext.entity();
                
                // Log approval event
                logger.info("Approved order with ID: {} for pet ID: {}", order.getId(), order.getPetId());
                
                // Order state will be automatically updated by the workflow
                // In a real implementation, this might validate payment information
                // or perform other approval-related business logic
                
                return order;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "OrderApprovalProcessor".equals(opSpec.operationName()) &&
               "Order".equals(opSpec.modelKey().getName()) &&
               opSpec.modelKey().getVersion() >= 1;
    }
}
