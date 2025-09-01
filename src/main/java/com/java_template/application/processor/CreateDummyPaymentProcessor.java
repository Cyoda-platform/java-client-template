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
import java.util.UUID;

@Component
public class CreateDummyPaymentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateDummyPaymentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateDummyPaymentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract payment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract payment entity: " + error.getMessage());
            })
            .validate(this::isValidPayment, "Invalid payment state")
            .map(this::processPaymentCreation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPayment(Payment payment) {
        return payment != null &&
               payment.getCartId() != null &&
               payment.getAmount() != null &&
               payment.getAmount() > 0;
    }

    private Payment processPaymentCreation(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();

        // Set payment ID if not present
        if (payment.getPaymentId() == null || payment.getPaymentId().isBlank()) {
            payment.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        // Set status to INITIATED
        payment.setStatus("INITIATED");

        // Set provider to DUMMY
        payment.setProvider("DUMMY");

        // Set timestamps
        String now = Instant.now().toString();
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

        logger.info("Created dummy payment {} for cart {} with amount {}",
                   payment.getPaymentId(), payment.getCartId(), payment.getAmount());

        return payment;
    }
}