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
 * Processor for auto-marking payment as paid after 3 seconds
 * Simulates dummy payment processing delay
 */
@Component
public class AutoMarkPaidAfter3s implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AutoMarkPaidAfter3s.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AutoMarkPaidAfter3s(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Payment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid payment entity wrapper")
                .map(this::autoMarkPaidWithContext)
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
     * Auto-mark payment as paid after 3 seconds delay
     */
    private EntityWithMetadata<Payment> autoMarkPaidWithContext(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {
        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();
        logger.info("Auto-marking payment as paid: {}", payment.getPaymentId());

        try {
            // Simulate 3-second processing delay
            logger.info("Simulating 3-second payment processing for payment: {}", payment.getPaymentId());
            Thread.sleep(3000);

            // Mark payment as paid
            payment.setStatus("PAID");
            payment.setPaidAt(LocalDateTime.now());
            payment.setUpdatedAt(LocalDateTime.now());

            logger.info("Payment {} marked as PAID after 3-second delay", payment.getPaymentId());
            
            return entityWithMetadata;

        } catch (InterruptedException e) {
            logger.error("Payment processing interrupted for payment: {}", payment.getPaymentId(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Payment processing interrupted", e);
        } catch (Exception e) {
            logger.error("Error auto-marking payment as paid: {}", payment.getPaymentId(), e);
            throw new RuntimeException("Failed to auto-mark payment as paid", e);
        }
    }
}
