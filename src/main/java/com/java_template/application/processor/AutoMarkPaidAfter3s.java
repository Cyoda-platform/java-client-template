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
 * AutoMarkPaidAfter3s Processor - Auto-approves dummy payment
 * 
 * Handles AUTO_MARK_PAID transition for Payment entity.
 * Simulates payment processing by waiting 3 seconds then marking as PAID.
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
                .map(this::processPaymentApproval)
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
        return payment != null && payment.isValid() && technicalId != null;
    }

    /**
     * Main business logic - auto-approve payment after 3 seconds
     */
    private EntityWithMetadata<Payment> processPaymentApproval(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {

        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        logger.debug("Auto-approving payment: {} after 3 seconds", payment.getPaymentId());

        try {
            // Simulate payment processing delay
            Thread.sleep(3000); // 3 seconds
            
            // Mark payment as PAID
            payment.setStatus("PAID");
            
            // Update timestamp
            payment.setUpdatedAt(LocalDateTime.now());

            logger.info("Payment {} auto-approved and marked as PAID, amount: {}", 
                       payment.getPaymentId(), payment.getAmount());

        } catch (InterruptedException e) {
            logger.warn("Payment processing interrupted for payment: {}", payment.getPaymentId());
            Thread.currentThread().interrupt();
            
            // Mark as failed if interrupted
            payment.setStatus("FAILED");
            payment.setUpdatedAt(LocalDateTime.now());
        }

        return entityWithMetadata;
    }
}
