package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
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

import java.util.concurrent.ThreadLocalRandom;

@Component
public class PaymentAuthorizationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentAuthorizationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentAuthorizationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment for authorization request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Payment entity) {
        return entity != null && entity.getAmount() != null;
    }

    private Payment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();
        if (payment == null) return null;

        String status = payment.getStatus();
        if ("AUTHORIZED".equalsIgnoreCase(status) || "CAPTURED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
            logger.info("Payment already in final state: {}", status);
            return payment;
        }

        payment.setStatus("PENDING");

        // simulate external gateway call (idempotent by paymentId)
        int outcome = ThreadLocalRandom.current().nextInt(0, 10);
        if (outcome < 7) {
            payment.setStatus("AUTHORIZED");
            // sometimes auth+capture
            if (outcome == 1) {
                payment.setStatus("CAPTURED");
            }
            payment.setProviderResponse(java.util.Collections.singletonMap("provider", "simulated"));
            logger.info("Payment {} authorized (simulated)", payment.getPaymentId());
        } else {
            payment.setStatus("FAILED");
            payment.setProviderResponse(java.util.Collections.singletonMap("providerError", "insufficient_funds"));
            logger.warn("Payment {} authorization failed (simulated)", payment.getPaymentId());
        }

        return payment;
    }
}
