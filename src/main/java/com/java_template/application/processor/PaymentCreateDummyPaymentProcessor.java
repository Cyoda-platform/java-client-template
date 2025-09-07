package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.dto.EntityWithMetadata;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Payment Create Dummy Payment Processor
 * 
 * Creates a dummy payment in INITIATED state.
 * Transitions: START_DUMMY_PAYMENT
 */
@Component
public class PaymentCreateDummyPaymentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCreateDummyPaymentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentCreateDummyPaymentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing dummy payment creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Payment.class)
            .validate(this::isValidEntityWithMetadata, "Invalid payment entity wrapper")
            .map(this::processPaymentCreation)
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
        return payment != null && payment.isValid() && entityWithMetadata.getId() != null;
    }

    /**
     * Main business logic for dummy payment creation
     */
    private EntityWithMetadata<Payment> processPaymentCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {
        
        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        logger.debug("Creating dummy payment for cart: {}", payment.getCartId());

        // Validate required fields
        if (payment.getCartId() == null || payment.getCartId().trim().isEmpty()) {
            throw new IllegalArgumentException("Cart ID is required for payment creation");
        }
        
        if (payment.getAmount() == null || payment.getAmount() <= 0) {
            throw new IllegalArgumentException("Valid amount is required for payment creation");
        }

        // Set dummy payment properties
        payment.setProvider("DUMMY");
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Dummy payment created for cart: {} with amount: {}", 
            payment.getCartId(), payment.getAmount());

        return entityWithMetadata;
    }
}
