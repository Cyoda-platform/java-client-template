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

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class PaymentAutoMarkPaidProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentAutoMarkPaidProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

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
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid payment state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Payment entity) {
        return entity != null && entity.isValid();
    }

    private Payment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();
        
        logger.info("Auto-marking payment as paid: {}", payment.getPaymentId());

        // Validate payment entity
        if (payment == null) {
            logger.error("Payment entity is null");
            throw new IllegalArgumentException("Payment entity cannot be null");
        }

        // Validate payment is in correct state for auto-marking
        // Note: The state validation should be handled by the workflow system,
        // but we can add additional business validation here
        
        if (payment.getPaymentId() == null || payment.getPaymentId().trim().isEmpty()) {
            logger.error("Payment ID is required");
            throw new IllegalArgumentException("Payment ID is required");
        }

        if (payment.getAmount() == null || payment.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            logger.error("Valid payment amount is required");
            throw new IllegalArgumentException("Valid payment amount is required");
        }

        if (!"DUMMY".equals(payment.getProvider())) {
            logger.error("Only DUMMY payments can be auto-marked as paid");
            throw new IllegalArgumentException("Invalid payment provider for auto-marking");
        }

        // Update timestamp to reflect the payment completion
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Payment {} auto-marked as paid for amount: {}", 
                   payment.getPaymentId(), payment.getAmount());

        // The state transition to PAID will be handled by the workflow system
        return payment;
    }
}
