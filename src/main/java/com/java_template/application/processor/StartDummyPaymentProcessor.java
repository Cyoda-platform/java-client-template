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
 * ABOUTME: Processor for starting dummy payment processing, setting payment status
 * to INITIATED and scheduling automatic payment completion after 3 seconds.
 */
@Component
public class StartDummyPaymentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartDummyPaymentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public StartDummyPaymentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::processPaymentStart)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for Payment
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Payment> entityWithMetadata) {
        Payment payment = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return payment != null && payment.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for starting payment
     */
    private EntityWithMetadata<Payment> processPaymentStart(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {

        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        logger.debug("Processing payment start for paymentId: {}", payment.getPaymentId());

        // Set payment status to INITIATED
        payment.setStatus("INITIATED");
        payment.setProvider("DUMMY");
        payment.setUpdatedAt(LocalDateTime.now());

        // Schedule automatic payment completion after 3 seconds
        scheduleAutoPayment(entityWithMetadata.metadata().getId());

        logger.info("Dummy payment started for paymentId: {}, amount: {}", 
                   payment.getPaymentId(), payment.getAmount());

        return entityWithMetadata;
    }

    /**
     * Schedule automatic payment completion after 3 seconds
     */
    private void scheduleAutoPayment(java.util.UUID paymentTechnicalId) {
        CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(() -> {
            try {
                logger.debug("Triggering auto payment completion for payment ID: {}", paymentTechnicalId);
                
                // Get current payment state
                ModelSpec modelSpec = new ModelSpec().withName(Payment.ENTITY_NAME).withVersion(Payment.ENTITY_VERSION);
                EntityWithMetadata<Payment> currentPayment = entityService.getById(paymentTechnicalId, modelSpec, Payment.class);
                
                if (currentPayment != null && "INITIATED".equals(currentPayment.entity().getStatus())) {
                    // Trigger auto payment transition
                    entityService.update(paymentTechnicalId, currentPayment.entity(), "auto_mark_paid");
                    logger.info("Auto payment transition triggered for payment ID: {}", paymentTechnicalId);
                } else {
                    logger.warn("Payment {} is no longer in INITIATED status, skipping auto payment", paymentTechnicalId);
                }
            } catch (Exception e) {
                logger.error("Failed to trigger auto payment for payment ID: {}", paymentTechnicalId, e);
            }
        });
    }
}
