package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FulfillmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FulfillmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public FulfillmentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Fulfillment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid order state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order order) {
        return order != null && order.isValid();
    }

    private ProcessorSerializer.ProcessorEntityExecutionContext<Order> processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        try {
            // For prototype we simply move PAID -> PROCESSING -> SHIPPED automatically for demo purposes
            if ("PAID".equalsIgnoreCase(order.getStatus())) {
                order.setStatus("PROCESSING");
            } else if ("PROCESSING".equalsIgnoreCase(order.getStatus())) {
                order.setStatus("SHIPPED");
            }
            if (context.attributes() != null && context.attributes().get("technicalId") != null) {
                String tid = (String) context.attributes().get("technicalId");
                entityService.updateItem(Order.ENTITY_NAME, String.valueOf(Order.ENTITY_VERSION), UUID.fromString(tid), order).whenComplete((id, ex) -> {
                    if (ex != null) logger.error("Failed to persist order status during fulfillment", ex);
                });
            }
        } catch (Exception ex) {
            logger.error("Error in FulfillmentProcessor", ex);
        }
        return context;
    }
}
