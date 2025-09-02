package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

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
        logger.info("Processing Payment auto mark paid for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract payment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract payment entity: " + error.getMessage());
            })
            .validate(this::isValidPaymentForAutoApproval, "Invalid payment state for auto approval")
            .map(this::autoMarkPaid)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPaymentForAutoApproval(Payment payment) {
        if (payment == null || !"DUMMY".equals(payment.getProvider())) {
            return false;
        }
        
        // Check if payment is in INITIATED state by checking the context
        // Note: In a real implementation, we would check the entity state from the context
        // For now, we'll assume the workflow ensures this processor only runs on INITIATED payments
        return true;
    }

    private Payment autoMarkPaid(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();

        logger.info("Auto-approving dummy payment: {}", payment.getPaymentId());

        try {
            // Simulate 3-second processing time
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Payment processing interrupted for payment: {}", payment.getPaymentId());
        }

        // Update timestamp to reflect payment approval
        payment.setUpdatedAt(Instant.now());

        logger.info("Dummy payment auto-approved: {}", payment.getPaymentId());

        return payment;
    }
}
