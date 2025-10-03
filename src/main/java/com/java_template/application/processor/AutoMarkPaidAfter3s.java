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
 * ABOUTME: Processor that simulates payment processing by waiting 3 seconds and then
 * automatically marking the payment as PAID for demo purposes.
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
                .map(this::processPaymentAfterDelay)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Payment> entityWithMetadata) {
        return entityWithMetadata != null && 
               entityWithMetadata.entity() != null && 
               entityWithMetadata.entity().isValid();
    }

    private EntityWithMetadata<Payment> processPaymentAfterDelay(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {
        
        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();
        
        logger.debug("Processing payment after 3s delay: {}", payment.getPaymentId());
        
        try {
            // Simulate 3-second payment processing delay
            Thread.sleep(3000);
            
            // Mark payment as PAID
            payment.setStatus("PAID");
            payment.setUpdatedAt(LocalDateTime.now());
            
            logger.info("Payment {} automatically marked as PAID after 3s delay", payment.getPaymentId());
            
        } catch (InterruptedException e) {
            logger.error("Payment processing interrupted for payment: {}", payment.getPaymentId(), e);
            Thread.currentThread().interrupt();
            
            // Mark as failed if interrupted
            payment.setStatus("FAILED");
            payment.setUpdatedAt(LocalDateTime.now());
        }
        
        return entityWithMetadata;
    }
}
