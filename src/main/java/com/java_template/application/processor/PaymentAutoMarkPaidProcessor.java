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
 * PaymentAutoMarkPaidProcessor - Auto-approves dummy payments
 * 
 * This processor handles:
 * - Automatically marking payment as PAID (simulating 3-second delay)
 * - Setting updated timestamp
 * - Logging payment approval
 * 
 * Triggered by: AUTO_MARK_PAID transition (after ~3 seconds)
 * 
 * Note: In a real implementation, this would involve actual payment gateway integration
 * For demo purposes, this processor simulates automatic payment approval
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
        logger.info("Processing payment auto-approval for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Payment.class)
            .validate(this::isValidEntityWithMetadata, "Invalid payment entity")
            .map(this::processPaymentApprovalLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the Payment EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Payment> entityWithMetadata) {
        Payment payment = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return payment != null && payment.isValid() && technicalId != null;
    }

    /**
     * Main business logic for payment auto-approval
     */
    private EntityWithMetadata<Payment> processPaymentApprovalLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {
        
        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        logger.debug("Auto-approving dummy payment: {}", payment.getPaymentId());

        // Simulate payment processing delay (in real implementation, this would be handled by external systems)
        // For demo purposes, we just log the approval
        
        // Update timestamp
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Dummy payment {} auto-approved successfully for cart: {}, amount: {}", 
                   payment.getPaymentId(), payment.getCartId(), payment.getAmount());

        return entityWithMetadata;
    }
}
