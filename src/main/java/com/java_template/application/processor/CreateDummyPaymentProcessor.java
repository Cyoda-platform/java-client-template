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
 * CreateDummyPaymentProcessor - Initializes dummy payment
 * 
 * Sets up a dummy payment with INITIATED status and DUMMY provider.
 * This processor runs when a payment is first created.
 */
@Component
public class CreateDummyPaymentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateDummyPaymentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateDummyPaymentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

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
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return payment != null && payment.isValid() && technicalId != null;
    }

    /**
     * Main business logic for creating dummy payment
     */
    private EntityWithMetadata<Payment> processPaymentCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {

        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        logger.debug("Creating dummy payment for payment: {}", payment.getPaymentId());

        // Set payment status to INITIATED
        payment.setStatus("INITIATED");
        
        // Set provider to DUMMY
        payment.setProvider("DUMMY");
        
        // Update timestamps
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Dummy payment {} created with status INITIATED for cart {}", 
                   payment.getPaymentId(), payment.getCartId());

        return entityWithMetadata;
    }
}
