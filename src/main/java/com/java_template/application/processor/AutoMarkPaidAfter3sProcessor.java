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
 * ABOUTME: Processor that simulates dummy payment processing by automatically
 * marking payments as paid after a 3-second delay for demo purposes.
 */
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
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Payment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid payment entity wrapper")
                .map(this::processPaymentLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Payment> entityWithMetadata) {
        Payment payment = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return payment != null && payment.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Payment> processPaymentLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {

        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        logger.info("Auto-processing dummy payment: {} for amount: ${}", 
                   payment.getPaymentId(), payment.getAmount());

        try {
            // Simulate 3-second payment processing delay
            logger.debug("Simulating 3-second payment processing delay...");
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            logger.warn("Payment processing interrupted for payment: {}", payment.getPaymentId());
            Thread.currentThread().interrupt();
        }

        // Update timestamp to reflect processing completion
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Dummy payment {} auto-approved after 3 seconds", payment.getPaymentId());

        return entityWithMetadata;
    }
}
