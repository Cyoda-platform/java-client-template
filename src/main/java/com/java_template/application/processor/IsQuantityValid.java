package com.java_template.application.processor;

import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IsQuantityValid implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public IsQuantityValid(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("IsQuantityValid initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating quantity for request: {}", request.getId());

        // Extract Order entity
        Order order = serializer.extractEntity(request, Order.class);

        boolean isValid = order.getQuantity() != null && order.getQuantity() > 0;

        // Build response with success if quantity valid, error otherwise
        return serializer.withRequest(request)
            .map(jsonNode -> {
                logger.info("Quantity valid: {}", isValid);
                // This dummy mapping just returns the original JSON without changes
                return jsonNode;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "IsQuantityValid".equals(modelSpec.operationName()) &&
               "order".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}