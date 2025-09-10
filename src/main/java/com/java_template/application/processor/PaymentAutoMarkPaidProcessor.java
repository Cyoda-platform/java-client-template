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
 * PaymentAutoMarkPaidProcessor - Automatically marks dummy payment as PAID
 * 
 * This processor automatically marks a dummy payment as PAID after a delay, simulating payment processing.
 * Used in transition: AUTO_MARK_PAID
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
        logger.info("Processing automatic payment approval for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Payment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid payment entity wrapper")
                .map(this::processPaymentApprovalLogic)
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
        
        return payment != null && payment.isValid() && technicalId != null &&
               "Initiated".equals(currentState) && "DUMMY".equals(payment.getProvider());
    }

    /**
     * Main business logic for automatically approving dummy payment
     */
    private EntityWithMetadata<Payment> processPaymentApprovalLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {

        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Auto-approving dummy payment: {} in state: {}", payment.getPaymentId(), currentState);

        // Simulate payment processing completion
        if ("DUMMY".equals(payment.getProvider())) {
            // Update timestamp to mark processing completion
            payment.setUpdatedAt(LocalDateTime.now());
            
            logger.info("Dummy payment {} automatically approved for cart {} with amount {}", 
                       payment.getPaymentId(), payment.getCartId(), payment.getAmount());
        } else {
            logger.warn("Attempted to auto-approve non-dummy payment: {}", payment.getPaymentId());
        }

        // The payment state will be automatically updated to PAID by the workflow
        return entityWithMetadata;
    }
}
