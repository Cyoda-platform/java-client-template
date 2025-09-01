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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class AutoMarkPaidAfter3sProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AutoMarkPaidAfter3sProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AutoMarkPaidAfter3sProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment auto-mark-paid for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract payment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract payment entity: " + error.getMessage());
            })
            .validate(this::isValidPaymentForAutoPaid, "Invalid payment state for auto-paid")
            .map(this::processAutoMarkPaid)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPaymentForAutoPaid(Payment payment) {
        return payment != null &&
               payment.getPaymentId() != null &&
               "INITIATED".equals(payment.getStatus()) &&
               payment.getCreatedAt() != null;
    }

    private Payment processAutoMarkPaid(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();

        // Check if enough time has passed (3 seconds)
        try {
            Instant createdAt = Instant.parse(payment.getCreatedAt());
            Instant now = Instant.now();
            long secondsPassed = ChronoUnit.SECONDS.between(createdAt, now);

            if (secondsPassed >= 3) {
                // Mark as PAID
                payment.setStatus("PAID");
                payment.setUpdatedAt(now.toString());

                logger.info("Auto-marked payment {} as PAID after {} seconds",
                           payment.getPaymentId(), secondsPassed);
            } else {
                logger.info("Payment {} not ready for auto-paid yet, only {} seconds passed",
                           payment.getPaymentId(), secondsPassed);
            }
        } catch (Exception e) {
            logger.error("Error parsing payment creation time for {}: {}", payment.getPaymentId(), e.getMessage());
        }

        return payment;
    }
}