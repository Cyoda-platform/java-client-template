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
public class PaymentAutoApprovalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentAutoApprovalProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentAutoApprovalProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment auto approval for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidPayment, "Invalid payment state")
            .map(this::processPaymentAutoApproval)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPayment(Payment payment) {
        return payment != null && payment.isValid();
    }

    private Payment processPaymentAutoApproval(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();

        // Validate payment is in INITIATED state
        String currentState = context.request().getPayload().getMeta().get("state").toString();
        if (!"INITIATED".equals(currentState)) {
            throw new IllegalStateException("Payment must be in INITIATED state to auto-approve");
        }

        // Validate 3 seconds have passed since creation
        if (payment.getCreatedAt() != null) {
            Instant now = Instant.now();
            long secondsPassed = ChronoUnit.SECONDS.between(payment.getCreatedAt(), now);
            
            if (secondsPassed < 3) {
                throw new IllegalStateException("Payment approval time not reached. Only " + secondsPassed + " seconds have passed");
            }
        }

        // Set updatedAt timestamp
        payment.setUpdatedAt(Instant.now());

        logger.info("Payment {} auto-approved successfully", payment.getPaymentId());
        return payment;
    }
}
