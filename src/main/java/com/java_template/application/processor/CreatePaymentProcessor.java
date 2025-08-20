package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CreatePaymentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreatePaymentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreatePaymentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for CreatePayment request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        return entity != null && entity.getOrderId() != null;
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        if (order == null) return null;

        String status = order.getStatus();
        if ("PAYMENT_PENDING".equalsIgnoreCase(status) || "CONFIRMED".equalsIgnoreCase(status)) {
            logger.info("Order already in payment state: {}", status);
            return order;
        }

        // Idempotent: check metadata for existing paymentTechnicalId
        try {
            Object existing = null;
            if (order.getMetadata() instanceof Map) {
                existing = ((Map<?,?>) order.getMetadata()).get("paymentTechnicalId");
            }
            if (existing != null) {
                logger.info("Payment already exists for order {}: {}", order.getOrderId(), existing);
                order.setStatus("PAYMENT_PENDING");
                return order;
            }
        } catch (Exception ignored) {}

        // Build Payment payload
        Payment p = new Payment();
        p.setOrderId(order.getOrderId());
        p.setAmount(order.getTotalAmount());
        p.setCurrency(order.getCurrency());
        p.setMethod("card");
        p.setStatus("PENDING");
        p.setPaymentId("PAY-" + UUID.randomUUID().toString());

        try {
            CompletableFuture<java.util.UUID> future = entityService.addItem(
                Payment.ENTITY_NAME,
                String.valueOf(Payment.ENTITY_VERSION),
                p
            );
            UUID created = future.get();
            logger.info("Created Payment for order {} technicalId={}", order.getOrderId(), created);
            // store reference in order metadata for idempotency
            try {
                Map<String,Object> meta = order.getMetadata() == null || !(order.getMetadata() instanceof Map) ? new java.util.HashMap<>() : (Map<String,Object>) order.getMetadata();
                meta.put("paymentTechnicalId", created.toString());
                order.setMetadata(meta);
            } catch (Exception ignored) {}

            order.setStatus("PAYMENT_PENDING");
        } catch (Exception e) {
            logger.warn("Failed to create payment for order {}: {}", order.getOrderId(), e.getMessage());
            // keep order in INVENTORY_RESERVED but add failure metadata
            try {
                Map<String,Object> meta = order.getMetadata() == null || !(order.getMetadata() instanceof Map) ? new java.util.HashMap<>() : (Map<String,Object>) order.getMetadata();
                meta.put("paymentCreateError", e.getMessage());
                order.setMetadata(meta);
            } catch (Exception ignored) {}
        }

        return order;
    }
}
