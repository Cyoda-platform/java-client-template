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
 * Processor to initialize a dummy payment with INITIATED status.
 * Sets up the payment for auto-approval after 3 seconds.
 */
@Component
public class CreateDummyPayment implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateDummyPayment.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateDummyPayment(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Payment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid payment entity wrapper")
                .map(this::initializeDummyPayment)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that the entity wrapper contains a valid payment
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Payment> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or Payment entity is null");
            return false;
        }

        Payment payment = entityWithMetadata.entity();
        if (!payment.isValid()) {
            logger.error("Payment entity validation failed for paymentId: {}", payment.getPaymentId());
            return false;
        }

        return true;
    }

    /**
     * Initializes the dummy payment with INITIATED status and DUMMY provider
     */
    private EntityWithMetadata<Payment> initializeDummyPayment(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {
        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();
        logger.info("Initializing dummy payment: {}", payment.getPaymentId());

        // Set payment status and provider
        payment.setStatus("INITIATED");
        payment.setProvider("DUMMY");
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Dummy payment {} initialized with status: {}, provider: {}",
                   payment.getPaymentId(), payment.getStatus(), payment.getProvider());

        return entityWithMetadata;
    }
}
