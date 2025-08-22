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

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // Only initiate payment when order is in a confirmed state
        String currentStatus = entity.getStatus();
        String currentPaymentStatus = entity.getPaymentStatus();

        if (currentStatus != null && currentStatus.equalsIgnoreCase("CONFIRMED")) {
            // If payment already initiated or completed, do nothing
            if (currentPaymentStatus != null && (currentPaymentStatus.equalsIgnoreCase("PENDING")
                    || currentPaymentStatus.equalsIgnoreCase("PAID"))) {
                logger.info("Order {} already has paymentStatus={}, skipping initiation", entity.getId(), entity.getPaymentStatus());
                return entity;
            }

            // Set order into waiting for payment and mark payment as pending
            entity.setStatus("WAITING_FOR_PAYMENT");
            entity.setPaymentStatus("PENDING");

            // In a real implementation here we would call the payment gateway to create a payment request.
            // Per constraints we do not call external services or persist this entity manually - Cyoda will persist changes.
            logger.info("Initiated payment for Order {}: set status='{}', paymentStatus='{}'",
                    entity.getId(), entity.getStatus(), entity.getPaymentStatus());

            return entity;
        } else {
            // If order is not confirmed, do not initiate payment. Log and leave entity unchanged.
            logger.warn("Order {} is in status '{}', payment initiation skipped. Expected 'CONFIRMED'.", entity.getId(), currentStatus);
            return entity;
        }
    }
}