package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * ABOUTME: Processor for automatically marking dummy payments as paid after 3 seconds
 * to simulate payment processing delay in the demo system.
 */
@Component
public class AutoMarkPaidAfter3sProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AutoMarkPaidAfter3sProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AutoMarkPaidAfter3sProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Payment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid payment entity wrapper")
                .map(this::scheduleAutoPayment)
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
        return payment != null && payment.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Schedule automatic payment approval after 3 seconds
     */
    private EntityWithMetadata<Payment> scheduleAutoPayment(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {

        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();
        java.util.UUID paymentTechnicalId = entityWithMetadata.metadata().getId();

        logger.debug("Scheduling auto-payment for payment: {}", payment.getPaymentId());

        // Schedule async payment approval after 3 seconds
        CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(() -> {
            try {
                logger.info("Auto-approving payment: {}", payment.getPaymentId());
                
                // Get current payment state
                ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
                EntityWithMetadata<Payment> currentPayment = entityService.getById(paymentTechnicalId, modelSpec, Payment.class);
                
                if (currentPayment != null && "INITIATED".equals(currentPayment.entity().getStatus())) {
                    // Update payment to PAID status
                    Payment updatedPayment = currentPayment.entity();
                    updatedPayment.setStatus("PAID");
                    updatedPayment.setUpdatedAt(LocalDateTime.now());
                    
                    entityService.update(paymentTechnicalId, updatedPayment, "auto_mark_paid");
                    logger.info("Payment {} automatically marked as PAID", payment.getPaymentId());
                } else {
                    logger.warn("Payment {} is no longer in INITIATED status, skipping auto-approval", payment.getPaymentId());
                }
            } catch (Exception e) {
                logger.error("Failed to auto-approve payment: {}", payment.getPaymentId(), e);
            }
        });

        // Update timestamp for initial processing
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Auto-payment scheduled for payment: {} (will be paid in 3 seconds)", payment.getPaymentId());

        return entityWithMetadata;
    }
}
