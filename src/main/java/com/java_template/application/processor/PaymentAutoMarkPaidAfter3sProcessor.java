package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.dto.EntityWithMetadata;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Payment Auto Mark Paid After 3s Processor
 * 
 * Automatically marks payment as paid after ~3 seconds delay.
 * Transitions: AUTO_MARK_PAID
 */
@Component
public class PaymentAutoMarkPaidAfter3sProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentAutoMarkPaidAfter3sProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentAutoMarkPaidAfter3sProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing auto payment approval for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Payment.class)
            .validate(this::isValidEntityWithMetadata, "Invalid payment entity wrapper")
            .map(this::processAutoPaymentApproval)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Payment> entityWithMetadata) {
        Payment payment = entityWithMetadata.entity();
        return payment != null && payment.isValid() && entityWithMetadata.getId() != null;
    }

    /**
     * Main business logic for auto payment approval
     */
    private EntityWithMetadata<Payment> processAutoPaymentApproval(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {
        
        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        logger.debug("Auto-approving payment: {}", payment.getPaymentId());

        // Validate payment is in correct state and provider
        if (!"DUMMY".equals(payment.getProvider())) {
            throw new IllegalStateException("Auto-approval only supported for DUMMY payments");
        }
        
        String currentState = entityWithMetadata.getState();
        if (!"initiated".equals(currentState)) {
            throw new IllegalStateException("Payment must be in INITIATED state for auto-approval, current state: " + currentState);
        }

        // Simulate 3-second processing delay
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Payment processing interrupted for payment: {}", payment.getPaymentId());
        }

        // Update payment timestamp
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Payment auto-approved: {} for cart: {}", 
            payment.getPaymentId(), payment.getCartId());

        return entityWithMetadata;
    }
}
