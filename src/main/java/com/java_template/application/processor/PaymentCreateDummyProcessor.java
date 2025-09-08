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
 * PaymentCreateDummyProcessor - Initializes a dummy payment
 * 
 * This processor handles:
 * - Setting payment status to INITIATED
 * - Setting provider to DUMMY
 * - Setting creation timestamp
 * - Preparing payment for auto-approval after 3 seconds
 * 
 * Triggered by: START_DUMMY_PAYMENT transition
 */
@Component
public class PaymentCreateDummyProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCreateDummyProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentCreateDummyProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing dummy payment creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Payment.class)
            .validate(this::isValidEntityWithMetadata, "Invalid payment entity")
            .map(this::processPaymentCreationLogic)
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
        return payment != null && technicalId != null;
    }

    /**
     * Main business logic for dummy payment creation
     */
    private EntityWithMetadata<Payment> processPaymentCreationLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {
        
        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        logger.debug("Initializing dummy payment: {}", payment.getPaymentId());

        // Set payment provider to DUMMY
        payment.setProvider("DUMMY");

        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        if (payment.getCreatedAt() == null) {
            payment.setCreatedAt(now);
        }
        payment.setUpdatedAt(now);

        logger.info("Dummy payment {} initialized successfully for cart: {}, amount: {}", 
                   payment.getPaymentId(), payment.getCartId(), payment.getAmount());

        return entityWithMetadata;
    }
}
