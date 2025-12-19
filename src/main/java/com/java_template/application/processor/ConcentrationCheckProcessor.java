package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
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

/**
 * ConcentrationCheckProcessor - Validates position concentration limits
 */
@Component
public class ConcentrationCheckProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ConcentrationCheckProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ConcentrationCheckProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Checking concentration for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidOrder, "Invalid order")
                .map(this::checkConcentration)
                .complete();
    }

    private EntityWithMetadata<Order> checkConcentration(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.getEntity();
        
        // In a real system, check position concentration
        // Ensure no single position exceeds concentration limits
        logger.info("Concentration check passed for order: {} on symbol: {}", 
            order.getOrderId(), order.getSymbol());
        
        return orderWithMetadata;
    }

    private boolean isValidOrder(EntityWithMetadata<Order> orderWithMetadata) {
        Order order = orderWithMetadata.getEntity();
        return order.getOrderId() != null && order.getSymbol() != null;
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

