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
 * AutoMarkPaidAfter3sProcessor - Handles automatic payment approval
 * 
 * This processor simulates payment processing by automatically
 * marking payments as PAID after a 3-second delay.
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
        return payment != null && payment.isValid() && technicalId != null;
    }

    /**
     * Main auto payment processing logic
     */
    private EntityWithMetadata<Payment> processAutoPayment(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {

        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();
        java.util.UUID paymentTechnicalId = entityWithMetadata.metadata().getId();

        logger.info("Starting auto-payment process for payment: {} (amount: {})", 
                payment.getPaymentId(), payment.getAmount());

        // Set status to INITIATED if not already set
        if (!"INITIATED".equals(payment.getStatus())) {
            payment.setStatus("INITIATED");
        }

        // Schedule auto-approval after 3 seconds
        scheduleAutoApproval(paymentTechnicalId, payment.getPaymentId());

        return entityWithMetadata;
    }

    /**
     * Schedule automatic payment approval after 3 seconds
     */
    private void scheduleAutoApproval(java.util.UUID paymentTechnicalId, String paymentId) {
        CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(() -> {
            try {
                logger.info("Auto-approving payment: {}", paymentId);
                
                // Get current payment state
                org.cyoda.cloud.api.event.common.ModelSpec modelSpec = 
                        new org.cyoda.cloud.api.event.common.ModelSpec()
                                .withName(Payment.ENTITY_NAME)
                                .withVersion(Payment.ENTITY_VERSION);
                
                EntityWithMetadata<Payment> currentPayment = entityService.getById(
                        paymentTechnicalId, modelSpec, Payment.class);
                
                if (currentPayment == null) {
                    logger.warn("Payment not found for auto-approval: {}", paymentId);
                    return;
                }

                Payment payment = currentPayment.entity();
                
                // Only approve if still in INITIATED status
                if ("INITIATED".equals(payment.getStatus())) {
                    payment.setStatus("PAID");
                    payment.setPaidAt(LocalDateTime.now());
                    payment.setUpdatedAt(LocalDateTime.now());
                    
                    // Update payment with transition
                    entityService.update(paymentTechnicalId, payment, "auto_mark_paid");
                    
                    logger.info("Payment auto-approved successfully: {}", paymentId);
                } else {
                    logger.info("Payment {} not in INITIATED status, skipping auto-approval. Current status: {}", 
                            paymentId, payment.getStatus());
                }
                
            } catch (Exception e) {
                logger.error("Error during auto-approval for payment: {}", paymentId, e);
                
                // Try to mark as failed
                try {
                    org.cyoda.cloud.api.event.common.ModelSpec modelSpec = 
                            new org.cyoda.cloud.api.event.common.ModelSpec()
                                    .withName(Payment.ENTITY_NAME)
                                    .withVersion(Payment.ENTITY_VERSION);
                    
                    EntityWithMetadata<Payment> currentPayment = entityService.getById(
                            paymentTechnicalId, modelSpec, Payment.class);
                    
                    if (currentPayment != null) {
                        Payment payment = currentPayment.entity();
                        payment.setStatus("FAILED");
                        payment.setFailureReason("Auto-approval failed: " + e.getMessage());
                        payment.setUpdatedAt(LocalDateTime.now());
                        
                        entityService.update(paymentTechnicalId, payment, "mark_failed");
                        logger.info("Payment marked as failed due to auto-approval error: {}", paymentId);
                    }
                } catch (Exception failureException) {
                    logger.error("Failed to mark payment as failed: {}", paymentId, failureException);
                }
            }
        });
    }
}
