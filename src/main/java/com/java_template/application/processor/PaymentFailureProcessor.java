package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

@Component
public class PaymentFailureProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentFailureProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentFailureProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Payment entity) {
        return entity != null && entity.isValid();
    }

    private Payment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment entity = context.entity();
        if (entity == null) {
            logger.warn("Payment entity is null in execution context");
            return entity;
        }

        String status = entity.getStatus();
        if (status == null) {
            logger.warn("Payment {} has no status, skipping failure evaluation", entity.getPaymentId());
            return entity;
        }

        // Only consider payments that are still INITIATED for failure evaluation
        if (!"INITIATED".equalsIgnoreCase(status)) {
            logger.debug("Payment {} status is not INITIATED ({}), skipping", entity.getPaymentId(), status);
            return entity;
        }

        boolean shouldFail = false;

        // Rule 1: Non-dummy providers are considered failed in this demo failure processor
        String provider = entity.getProvider();
        if (provider == null || !provider.equalsIgnoreCase("DUMMY")) {
            logger.info("Marking payment {} as FAILED because provider is invalid or non-DUMMY: {}", entity.getPaymentId(), provider);
            shouldFail = true;
        }

        // Rule 2: Invalid amount (null or non-positive) -> fail
        Double amount = entity.getAmount();
        if (!shouldFail) {
            if (amount == null || amount <= 0.0) {
                logger.info("Marking payment {} as FAILED due to invalid amount: {}", entity.getPaymentId(), amount);
                shouldFail = true;
            }
        }

        // Rule 3: If payment has been INITIATED for too long (stale), mark as failed (fallback).
        // Use a conservative threshold (e.g., 30 seconds) as a demo timeout.
        if (!shouldFail) {
            String createdAt = entity.getCreatedAt();
            if (createdAt != null) {
                try {
                    Instant createdInstant = Instant.parse(createdAt);
                    Instant now = Instant.now();
                    long secondsElapsed = Duration.between(createdInstant, now).getSeconds();
                    if (secondsElapsed > 30) {
                        logger.info("Marking payment {} as FAILED due to timeout ({}s elapsed)", entity.getPaymentId(), secondsElapsed);
                        shouldFail = true;
                    }
                } catch (DateTimeParseException ex) {
                    // If timestamp cannot be parsed, do not infer timeout; log and continue.
                    logger.debug("Unable to parse createdAt for payment {}: {}", entity.getPaymentId(), createdAt);
                }
            } else {
                logger.debug("Payment {} has no createdAt timestamp to evaluate timeout", entity.getPaymentId());
            }
        }

        if (shouldFail) {
            entity.setStatus("FAILED");
            try {
                entity.setUpdatedAt(Instant.now().toString());
            } catch (Exception ex) {
                logger.debug("Failed to set updatedAt for payment {}: {}", entity.getPaymentId(), ex.getMessage());
            }
            logger.info("Payment {} transitioned to FAILED by PaymentFailureProcessor", entity.getPaymentId());
        } else {
            logger.debug("Payment {} remains in INITIATED state after failure evaluation", entity.getPaymentId());
        }

        return entity;
    }
}