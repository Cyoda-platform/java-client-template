package com.java_template.application.processor;

import com.java_template.application.entity.cartorder.version_1.CartOrder;
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

@Component
public class MarkShippedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MarkShippedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MarkShippedProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MarkShipped for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CartOrder.class)
            .validate(this::isValidEntity, "Invalid cart order state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CartOrder entity) {
        return entity != null && entity.isValid();
    }

    private CartOrder processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CartOrder> context) {
        CartOrder order = context.entity();
        try {
            order.setStatus("Shipped");
            logger.info("Order {} marked as Shipped", order.getOrderId());
            return order;
        } catch (Exception e) {
            logger.error("Error marking order {} as shipped: {}", order.getOrderId(), e.getMessage(), e);
            return order;
        }
    }
}
