package com.java_template.application.processor;

import com.java_template.application.entity.Order;
import com.java_template.common.serializer.ErrorInfo;
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
public class OrderProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public OrderProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("OrderProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidOrder, "Invalid order state")
            .map(this::processOrderLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "OrderProcessor".equals(modelSpec.operationName()) &&
               "order".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidOrder(Order order) {
        return order.isValid();
    }

    private Order processOrderLogic(Order order) {
        logger.info("Processing Order with orderId: {}", order.getOrderId());

        // Validation: Check order details (product availability simulated)
        if (order.getQuantity() <= 0) {
            logger.error("Order validation failed: quantity must be greater than zero");
            order.setStatus("FAILED");
            return order;
        }
        order.setStatus("PROCESSING");

        // Processing: Reserve inventory, initiate shipping process (simulated)
        logger.info("Reserving inventory for productCode: {}", order.getProductCode());

        // Simulate shipped status
        order.setStatus("SHIPPED");
        logger.info("Order processing completed successfully");

        return order;
    }
}
