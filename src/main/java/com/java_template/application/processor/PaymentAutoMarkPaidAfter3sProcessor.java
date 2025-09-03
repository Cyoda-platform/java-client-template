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
        logger.info("Processing Payment auto mark paid for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract payment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract payment entity: " + error.getMessage());
            })
            .validate(this::isValidPayment, "Invalid payment state")
            .map(this::processPaymentLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPayment(Payment payment) {
        return payment != null && payment.isValid();
    }

    private Payment processPaymentLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();
        EntityProcessorCalculationRequest request = context.request();

        try {
            // Get current payment state from metadata
            String currentState = request.getPayload().getMeta().get("state").asText();
            
            if (!"initiated".equals(currentState)) {
                throw new RuntimeException("Payment must be in initiated state, current state: " + currentState);
            }

            if (!"DUMMY".equals(payment.getProvider())) {
                throw new RuntimeException("Auto-payment only supported for DUMMY provider, current provider: " + payment.getProvider());
            }

            // Simulate 3-second processing delay
            logger.info("Simulating 3-second payment processing delay for payment: {}", payment.getPaymentId());
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Payment processing delay interrupted for payment: {}", payment.getPaymentId());
            }

            logger.info("Payment auto-marked as paid: {}", payment.getPaymentId());
            
            // Payment will transition to PAID state automatically by workflow
            // No entity modification needed - state managed by workflow
            return payment;

        } catch (Exception e) {
            logger.error("Error processing payment auto-mark paid: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to auto-mark payment as paid: " + e.getMessage(), e);
        }
    }
}
