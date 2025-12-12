package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * ChargePayment Processor
 * 
 * Simulates payment charging for an order.
 * In a real system, this would integrate with a payment gateway.
 */
@Component
public class ChargePayment implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ChargePayment.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final Random random = new Random();

    public ChargePayment(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ChargePayment for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::chargePaymentLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Order> chargePaymentLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.info("Charging payment for order: {} with amount: {}", 
                order.getOrderId(), order.getTotalAmount());

        // Simulate payment processing
        boolean paymentSuccessful = simulatePaymentGateway(order.getTotalAmount());

        if (!paymentSuccessful) {
            logger.error("Payment failed for order: {}", order.getOrderId());
            throw new RuntimeException("Payment processing failed");
        }

        // Store payment information in metadata
        if (order.getMetadata() == null) {
            order.setMetadata(new HashMap<>());
        }
        
        Map<String, Object> paymentInfo = new HashMap<>();
        paymentInfo.put("transactionId", generateTransactionId());
        paymentInfo.put("amount", order.getTotalAmount());
        paymentInfo.put("currency", order.getCurrency());
        paymentInfo.put("status", "CHARGED");
        paymentInfo.put("timestamp", System.currentTimeMillis());
        
        order.getMetadata().put("payment", paymentInfo);

        logger.info("Payment charged successfully for order: {}", order.getOrderId());
        return entityWithMetadata;
    }

    /**
     * Simulates payment gateway processing
     * In a real system, this would call an actual payment provider API
     */
    private boolean simulatePaymentGateway(BigDecimal amount) {
        // Simulate 95% success rate
        return random.nextDouble() < 0.95;
    }

    /**
     * Generates a unique transaction ID
     */
    private String generateTransactionId() {
        return "TXN-" + System.currentTimeMillis() + "-" + random.nextInt(10000);
    }
}

