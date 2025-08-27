package com.java_template.application.processor;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;

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
        logger.info("Processing Payment for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Payment.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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
        Payment payment = context.entity();

        // Only auto-mark payments that are currently INITIATED
        if (payment.getStatus() == null) {
            logger.debug("Payment {} has null status, skipping auto-mark", payment.getPaymentId());
            return payment;
        }

        if (!"INITIATED".equalsIgnoreCase(payment.getStatus())) {
            logger.debug("Payment {} status is '{}' - no auto action required", payment.getPaymentId(), payment.getStatus());
            return payment;
        }

        String createdAtStr = payment.getCreatedAt();
        if (createdAtStr == null || createdAtStr.isBlank()) {
            logger.warn("Payment {} createdAt is missing, cannot evaluate delay - skipping", payment.getPaymentId());
            return payment;
        }

        try {
            Instant createdAt = Instant.parse(createdAtStr);
            Instant now = Instant.now();
            Instant threshold = createdAt.plusSeconds(3);

            if (now.isAfter(threshold) || now.equals(threshold)) {
                payment.setStatus("PAID");
                payment.setUpdatedAt(Instant.now().toString());
                logger.info("Auto-marked payment {} as PAID (was INITIATED, createdAt={})", payment.getPaymentId(), createdAtStr);
            } else {
                logger.debug("Payment {} not old enough to auto-mark (createdAt={}, threshold={})", payment.getPaymentId(), createdAtStr, threshold.toString());
            }
        } catch (DateTimeParseException e) {
            logger.warn("Failed to parse createdAt for payment {}: {} - skipping auto-mark", payment.getPaymentId(), e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while processing auto-mark for payment {}: {}", payment.getPaymentId(), e.getMessage(), e);
        }

        return payment;
    }
}