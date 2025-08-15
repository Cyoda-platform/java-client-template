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
import java.util.concurrent.CompletableFuture;

@Component
public class InitiatePaymentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(InitiatePaymentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public InitiatePaymentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InitiatePayment for request: {}", request.getId());

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
            // For prototype, simulate payment initiation by setting a fake paymentReference and persist
            String paymentRef = "payprov-" + UUID.randomUUID().toString();
            order.setPaymentReference(paymentRef);
            // Update order with payment reference
            if (context.attributes() != null && context.attributes().get("technicalId") != null) {
                String tid = (String) context.attributes().get("technicalId");
                entityService.updateItem(Order.ENTITY_NAME, String.valueOf(Order.ENTITY_VERSION), UUID.fromString(tid), order).whenComplete((id, ex) -> {
                    if (ex != null) logger.error("Failed to persist payment reference on order", ex);
                });
            }

            // In a real integration we'd call a payment gateway. For prototype we simply emit logs and rely on PaymentProcessor to consume callbacks.
            logger.info("Initiated payment for order {} with reference {}", order.getId(), paymentRef);
        } catch (Exception ex) {
            logger.error("Error initiating payment for order", ex);
        }
        return context;
    }
}
