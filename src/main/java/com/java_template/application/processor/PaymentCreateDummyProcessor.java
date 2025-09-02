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

@Component
public class PaymentCreateDummyProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCreateDummyProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentCreateDummyProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment create dummy for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract payment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract payment entity: " + error.getMessage());
            })
            .validate(this::isValidPayment, "Invalid payment state")
            .map(this::createDummyPayment)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPayment(Payment payment) {
        return payment != null && 
               payment.getCartId() != null && !payment.getCartId().trim().isEmpty() &&
               payment.getAmount() != null && payment.getAmount() > 0;
    }

    private Payment createDummyPayment(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();

        logger.info("Creating dummy payment for cart: {}, amount: {}", payment.getCartId(), payment.getAmount());

        // Set dummy payment provider
        payment.setProvider("DUMMY");
        
        // Set timestamps
        Instant now = Instant.now();
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

        logger.info("Dummy payment created for cart: {}, amount: {}", payment.getCartId(), payment.getAmount());

        return payment;
    }
}
