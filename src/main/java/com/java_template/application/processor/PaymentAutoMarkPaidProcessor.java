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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * PaymentAutoMarkPaidProcessor - Automatically marks payment as paid after approximately 3 seconds delay.
 * 
 * Transitions: AUTO_APPROVE
 * 
 * Business Logic:
 * - Simulates processing time with 3-second delay
 * - Updates timestamp
 * - State transition to PAID is handled by workflow engine
 */
@Component
public class PaymentAutoMarkPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentAutoMarkPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentAutoMarkPaidProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment auto-approval for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Payment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid payment entity wrapper")
                .map(this::processPaymentAutoApproval)
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
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        
        return payment != null && technicalId != null && 
               payment.getPaymentId() != null && "initiated".equals(currentState);
    }

    /**
     * Main business logic for payment auto-approval
     */
    private EntityWithMetadata<Payment> processPaymentAutoApproval(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {

        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Auto-approving payment: {} in state: {}", payment.getPaymentId(), currentState);

        // Simulate processing time with 3-second delay
        try {
            Thread.sleep(3000); // 3 seconds
            logger.debug("Payment processing delay completed for payment: {}", payment.getPaymentId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Payment processing delay interrupted for payment: {}", payment.getPaymentId());
        }

        // Update timestamp
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Payment {} auto-approved and ready for state transition to PAID", payment.getPaymentId());

        // Note: State transition to PAID is handled by workflow engine
        return entityWithMetadata;
    }
}
