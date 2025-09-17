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
 * AutoMarkPaidProcessor - Automatically marks payment as paid after ~3 seconds
 * 
 * This processor simulates dummy payment processing by automatically
 * approving payments after a short delay for demo purposes.
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
        logger.info("Processing Payment auto-approval for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Payment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid payment entity wrapper")
                .map(this::processAutoPayment)
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
        Payment entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main auto-payment processing logic
     * 
     * Simulates payment processing delay and automatically approves
     * the payment for demo purposes.
     */
    private EntityWithMetadata<Payment> processAutoPayment(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {

        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        // Get current entity metadata
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing auto-payment for payment: {} in state: {}", payment.getPaymentId(), currentState);

        // Simulate processing delay (~3 seconds)
        try {
            logger.info("Simulating payment processing delay for payment: {}", payment.getPaymentId());
            Thread.sleep(3000); // 3 seconds delay
        } catch (InterruptedException e) {
            logger.warn("Payment processing interrupted for payment: {}", payment.getPaymentId());
            Thread.currentThread().interrupt();
        }

        // Update payment timestamp
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Payment {} auto-approved successfully. Amount: {}", 
                   payment.getPaymentId(), payment.getAmount());

        // Return the entity (state transition will be handled by workflow)
        return entityWithMetadata;
    }
}
