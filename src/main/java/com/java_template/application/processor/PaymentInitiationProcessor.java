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

@Component
public class PaymentInitiationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentInitiationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentInitiationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

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

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.isValid();
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order entity = context.entity();

        try {
            String currentStatus = entity.getStatus();
            String currentPaymentStatus = entity.getPaymentStatus();

            // If payment already initiated or completed, skip
            if (currentPaymentStatus != null) {
                String ps = currentPaymentStatus.trim();
                if (!ps.isEmpty() && (ps.equalsIgnoreCase("PENDING") || ps.equalsIgnoreCase("PAID") || ps.equalsIgnoreCase("FAILED"))) {
                    logger.info("Order {} already has paymentStatus='{}', skipping initiation", entity.getId(), currentPaymentStatus);
                    return entity;
                }
            }

            if (currentStatus == null || currentStatus.isBlank()) {
                logger.warn("Order {} has null or blank status, skipping payment initiation", entity.getId());
                return entity;
            }

            // Only initiate payment for Confirmed orders
            if ("CONFIRMED".equalsIgnoreCase(currentStatus.trim())) {
                // Mark order awaiting payment and set payment status to pending.
                entity.setStatus("WAITING_FOR_PAYMENT");
                entity.setPaymentStatus("PENDING");

                logger.info("Order {} moved from '{}' to '{}' with paymentStatus='{}'.",
                        entity.getId(), currentStatus, entity.getStatus(), entity.getPaymentStatus());

                // NOTE: Do not call external persistence for this entity. Cyoda will persist the modified entity automatically.
                // Any actual integration with a payment gateway should be handled asynchronously by separate components/events.
            } else {
                logger.warn("Order {} is in status '{}', payment initiation skipped. Expected 'CONFIRMED'.", entity.getId(), currentStatus);
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in PaymentInitiationProcessor for order {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
        }

        return entity;
    }
}