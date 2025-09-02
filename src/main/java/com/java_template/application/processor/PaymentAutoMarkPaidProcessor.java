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

import java.time.LocalDateTime;

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
        logger.info("Processing Payment auto-mark paid for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract payment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract payment entity: " + error.getMessage());
            })
            .validate(this::isValidPayment, "Invalid payment state")
            .map(this::processPaymentAutoApproval)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPayment(Payment payment) {
        if (payment == null) {
            logger.error("Payment entity is null");
            return false;
        }

        // Validate payment is in INITIATED state
        // Note: We can't directly check the state here as it's managed by the workflow system
        // The workflow will only call this processor when payment is in INITIATED state

        // Validate provider is DUMMY
        if (!"DUMMY".equals(payment.getProvider())) {
            logger.error("Payment provider is not DUMMY: {}", payment.getProvider());
            return false;
        }

        // Validate amount is positive
        if (payment.getAmount() == null || payment.getAmount() <= 0) {
            logger.error("Payment amount is not positive: {}", payment.getAmount());
            return false;
        }

        return true;
    }

    private Payment processPaymentAutoApproval(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();
        
        logger.info("Auto-approving payment {} for amount {}", payment.getPaymentId(), payment.getAmount());

        try {
            // Simulate payment processing delay (3 seconds)
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            logger.warn("Payment processing sleep interrupted", e);
            Thread.currentThread().interrupt();
        }

        // Update payment timestamp
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Payment {} auto-approved for amount {}", payment.getPaymentId(), payment.getAmount());

        return payment;
    }
}
