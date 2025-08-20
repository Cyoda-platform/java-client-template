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
public class PaymentCaptureProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCaptureProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PaymentCaptureProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment for capture request: {}", request.getId());

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
        return entity != null && entity.getStatus() != null;
    }

    private Payment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment p = context.entity();
        if (p == null) return null;

        String status = p.getStatus();
        if ("CAPTURED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
            logger.info("Payment already in final capture state: {}", status);
            return p;
        }

        // Only capture if authorized
        if (!"AUTHORIZED".equalsIgnoreCase(status)) {
            logger.warn("Payment {} not authorized. Current status={}. Skipping capture.", p.getPaymentId(), status);
            return p;
        }

        // simulate capture
        int outcome = ThreadLocalRandom.current().nextInt(0, 10);
        if (outcome < 8) {
            p.setStatus("CAPTURED");
            try { p.setProviderResponse("{\"capture\":\"ok\"}"); } catch (Exception ignored) {}
            logger.info("Payment {} captured (simulated)", p.getPaymentId());
        } else {
            p.setStatus("FAILED");
            try { p.setProviderResponse("{\"captureError\":\"timeout\"}"); } catch (Exception ignored) {}
            logger.warn("Payment {} capture failed (simulated)", p.getPaymentId());
        }

        return p;
    }
}
