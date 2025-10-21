package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * ABOUTME: Processor for auto-marking payment as paid after 3 seconds
 * to simulate dummy payment processing with automatic approval.
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
                .map(this::processAutoPayment)
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
     * Process auto payment logic - schedules payment to be marked as paid after 3 seconds
     */
    private EntityWithMetadata<Payment> processAutoPayment(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {

        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        logger.debug("Processing auto payment for payment: {}", payment.getPaymentId());

        // Set status to INITIATED
        payment.setStatus("INITIATED");
        payment.setUpdatedAt(LocalDateTime.now());

        // Schedule auto-approval after 3 seconds
        scheduleAutoApproval(entityWithMetadata.metadata().getId(), payment.getPaymentId());

        logger.info("Payment initiated and scheduled for auto-approval: {}", payment.getPaymentId());

        return entityWithMetadata;
    }

    /**
     * Schedule automatic payment approval after 3 seconds
     */
    private void scheduleAutoApproval(java.util.UUID paymentTechnicalId, String paymentId) {
        CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(() -> {
            try {
                logger.info("Auto-approving payment after 3 seconds: {}", paymentId);
                
                // Get current payment state
                org.cyoda.cloud.api.event.common.ModelSpec modelSpec = 
                    new org.cyoda.cloud.api.event.common.ModelSpec()
                        .withName(Payment.ENTITY_NAME)
                        .withVersion(Payment.ENTITY_VERSION);
                
                EntityWithMetadata<Payment> currentPayment = entityService.getById(
                    paymentTechnicalId, modelSpec, Payment.class);
                
                if (currentPayment != null && "INITIATED".equals(currentPayment.entity().getStatus())) {
                    Payment payment = currentPayment.entity();
                    payment.setStatus("PAID");
                    payment.setUpdatedAt(LocalDateTime.now());
                    
                    // Update payment with transition to PAID state
                    entityService.update(paymentTechnicalId, payment, "auto_mark_paid");
                    logger.info("Payment auto-approved successfully: {}", paymentId);
                } else {
                    logger.warn("Payment not in INITIATED status, skipping auto-approval: {}", paymentId);
                }
            } catch (Exception e) {
                logger.error("Failed to auto-approve payment: {}", paymentId, e);
            }
        });
    }
}
