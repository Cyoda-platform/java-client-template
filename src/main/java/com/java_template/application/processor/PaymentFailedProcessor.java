package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class PaymentFailedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentFailedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentFailedProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for PaymentFailed request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.getOrderId() != null;
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        if (order == null) return null;

        String status = order.getStatus();
        if ("CANCELLED".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)) {
            logger.info("Order already in terminal state: {}", status);
            return order;
        }

        logger.info("Payment failure handling for order {}. Marking CANCELLED.", order.getOrderId());
        order.setStatus("CANCELLED");
        try {
            Map<String,Object> meta = order.getMetadata() == null || !(order.getMetadata() instanceof Map) ? new HashMap<>() : (Map<String,Object>) order.getMetadata();
            meta.put("paymentFailureHandledAt", Instant.now().toString());
            order.setMetadata(meta);
        } catch (Exception ignored) {}

        return order;
    }
}
