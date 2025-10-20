package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
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

import java.time.LocalDateTime;

/**
 * ABOUTME: Processor for automatically marking dummy payments as PAID,
 * completing the payment flow after the 3-second delay.
 */
@Component
public class AutoMarkPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AutoMarkPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AutoMarkPaidProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Payment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid payment entity wrapper")
                .map(this::processAutoMarkPaid)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for Payment
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Payment> entityWithMetadata) {
        Payment payment = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return payment != null && payment.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for auto marking payment as paid
     */
    private EntityWithMetadata<Payment> processAutoMarkPaid(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {

        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        logger.debug("Processing auto mark paid for paymentId: {}", payment.getPaymentId());

        // Validate payment is in correct state
        if (!"INITIATED".equals(payment.getStatus())) {
            logger.warn("Payment {} is not in INITIATED status, current status: {}", 
                       payment.getPaymentId(), payment.getStatus());
            return entityWithMetadata;
        }

        // Mark payment as PAID
        payment.setStatus("PAID");
        payment.setPaidAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Payment {} automatically marked as PAID, amount: {}", 
                   payment.getPaymentId(), payment.getAmount());

        return entityWithMetadata;
    }
}
