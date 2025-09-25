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
 * Processor to automatically mark a dummy payment as PAID after ~3 seconds.
 * This simulates payment processing delay for demo purposes.
 */
@Component
public class AutoMarkPaidAfter3s implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AutoMarkPaidAfter3s.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AutoMarkPaidAfter3s(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Payment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid payment entity wrapper")
                .map(this::autoMarkPaymentPaid)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that the entity wrapper contains a valid payment
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Payment> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or Payment entity is null");
            return false;
        }

        Payment payment = entityWithMetadata.entity();
        if (!payment.isValid()) {
            logger.error("Payment entity validation failed for paymentId: {}", payment.getPaymentId());
            return false;
        }

        return true;
    }

    /**
     * Simulates payment processing delay and marks payment as PAID
     */
    private Payment autoMarkPaymentPaid(EntityWithMetadata<Payment> entityWithMetadata) {
        Payment payment = entityWithMetadata.entity();
        logger.info("Auto-marking payment as PAID: {}", payment.getPaymentId());

        try {
            // Simulate 3-second payment processing delay
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            logger.warn("Payment processing delay interrupted for payment: {}", payment.getPaymentId());
            Thread.currentThread().interrupt();
        }

        // Mark payment as PAID
        payment.setStatus("PAID");
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Payment {} automatically marked as PAID after 3s delay", payment.getPaymentId());

        return payment;
    }
}
