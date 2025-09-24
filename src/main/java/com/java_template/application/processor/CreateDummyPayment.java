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
 * Processor for creating dummy payment
 * Initializes payment with INITIATED status
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
                .map(this::createDummyPaymentWithContext)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validate the entity wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Payment> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or entity is null");
            return false;
        }

        Payment payment = entityWithMetadata.entity();
        if (!payment.isValid()) {
            logger.error("Payment entity is not valid: {}", payment);
            return false;
        }

        return true;
    }

    /**
     * Create dummy payment with context
     */
    private EntityWithMetadata<Payment> createDummyPaymentWithContext(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {
        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();
        logger.info("Creating dummy payment: {}", payment.getPaymentId());

        try {
            // Set payment status to INITIATED
            payment.setStatus("INITIATED");
            payment.setProvider("DUMMY");
            payment.setCreatedAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            logger.info("Created dummy payment {} for cart {} with amount {}", 
                       payment.getPaymentId(), payment.getCartId(), payment.getAmount());
            
            return entityWithMetadata;

        } catch (Exception e) {
            logger.error("Error creating dummy payment: {}", payment.getPaymentId(), e);
            throw new RuntimeException("Failed to create dummy payment", e);
        }
    }
}
