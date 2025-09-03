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
            .validate(this::isValidPayment, "Invalid payment state")
            .map(this::processPaymentApproval)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPayment(Payment payment) {
        return payment != null && 
               payment.getPaymentId() != null && !payment.getPaymentId().trim().isEmpty() &&
               payment.getCartId() != null && !payment.getCartId().trim().isEmpty() &&
               payment.getAmount() != null && payment.getAmount() > 0 &&
               "DUMMY".equals(payment.getProvider());
    }

    private Payment processPaymentApproval(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();
        
        logger.info("Auto-approving dummy payment: {}", payment.getPaymentId());
        
        // CRITICAL: The payment entity already contains all the data we need
        // Never extract from request payload - use payment getters directly
        
        try {
            // Simulate 3-second payment processing delay
            logger.info("Simulating payment processing for payment {} with amount {}", 
                payment.getPaymentId(), payment.getAmount());
            
            Thread.sleep(3000);
            
            // Update payment timestamp to indicate processing completion
            payment.setUpdatedAt(Instant.now());
            
            logger.info("Payment {} auto-approved successfully after 3 seconds", payment.getPaymentId());
            
        } catch (InterruptedException e) {
            logger.warn("Payment processing interrupted for payment {}", payment.getPaymentId());
            Thread.currentThread().interrupt();
            // Still mark as processed since this is a dummy payment
            payment.setUpdatedAt(Instant.now());
        }
        
        return payment;
    }
}
