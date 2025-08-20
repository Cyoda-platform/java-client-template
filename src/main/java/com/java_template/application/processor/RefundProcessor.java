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
public class RefundProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RefundProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RefundProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment refund for request: {}", request.getId());

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

        if (!"CAPTURED".equalsIgnoreCase(p.getStatus())) {
            logger.warn("Payment {} not captured; cannot refund.", p.getPaymentId());
            return p;
        }

        int outcome = ThreadLocalRandom.current().nextInt(0, 10);
        if (outcome < 9) {
            p.setStatus("REFUNDED");
            try { p.setProviderResponse("{\"refund\":\"ok\"}"); } catch (Exception ignored) {}
            logger.info("Payment {} refunded (simulated)", p.getPaymentId());
        } else {
            try { p.setProviderResponse("{\"refundError\":\"gateway_timeout\"}"); } catch (Exception ignored) {}
            logger.warn("Payment {} refund failed (simulated)", p.getPaymentId());
        }

        return p;
    }
}
