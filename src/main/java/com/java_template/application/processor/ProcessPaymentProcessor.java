package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
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
import java.util.UUID;

/**
 * Processor to handle payment processing for orders
 * Simulates payment authorization and capture
 */
@Component
public class ProcessPaymentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessPaymentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProcessPaymentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order wrapper")
                .map(this::processPayment)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order order = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return order != null && order.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Order> processPayment(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing payment for order: {}", order.getOrderId());

        // Initialize payment if not exists
        if (order.getPayment() == null) {
            order.setPayment(new Order.Payment());
        }

        Order.Payment payment = order.getPayment();

        // Set payment amount and currency from order
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency(order.getCurrency());

        // Set default payment method if not provided
        if (payment.getMethod() == null) {
            payment.setMethod("credit_card");
        }

        // Simulate payment processing
        try {
            // Generate transaction reference
            String transactionRef = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            payment.setTransactionRef(transactionRef);

            // Simulate payment authorization
            authorizePayment(payment);

            // If authorization successful, capture payment
            if ("authorized".equals(payment.getStatus())) {
                capturePayment(payment);
            }

            logger.info("Payment processed successfully for order: {} with transaction: {}", 
                       order.getOrderId(), payment.getTransactionRef());

        } catch (Exception e) {
            logger.error("Payment processing failed for order: {}", order.getOrderId(), e);
            handlePaymentFailure(payment, e.getMessage());
        }

        // Update order timestamp
        order.setUpdatedTimestamp(LocalDateTime.now());
        order.setLastUpdatedBy("ProcessPaymentProcessor");

        return entityWithMetadata;
    }

    private void authorizePayment(Order.Payment payment) {
        // Simulate payment authorization logic
        // In real implementation, this would call external payment gateway
        
        logger.debug("Authorizing payment of {} {}", payment.getAmount(), payment.getCurrency());
        
        // Simulate authorization delay
        try {
            Thread.sleep(100); // Simulate network call
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Simulate 95% success rate
        if (Math.random() < 0.95) {
            payment.setStatus("authorized");
            payment.setAuthorizedAt(LocalDateTime.now());
            logger.debug("Payment authorized successfully");
        } else {
            throw new RuntimeException("Payment authorization failed - insufficient funds");
        }
    }

    private void capturePayment(Order.Payment payment) {
        // Simulate payment capture logic
        // In real implementation, this would capture the authorized amount
        
        logger.debug("Capturing authorized payment");
        
        // Simulate capture delay
        try {
            Thread.sleep(50); // Simulate network call
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Simulate 99% capture success rate (higher than authorization)
        if (Math.random() < 0.99) {
            payment.setStatus("captured");
            payment.setCapturedAt(LocalDateTime.now());
            logger.debug("Payment captured successfully");
        } else {
            payment.setStatus("capture_failed");
            payment.setFailedAt(LocalDateTime.now());
            payment.setFailureReason("Payment capture failed - gateway timeout");
            throw new RuntimeException("Payment capture failed - gateway timeout");
        }
    }

    private void handlePaymentFailure(Order.Payment payment, String reason) {
        payment.setStatus("failed");
        payment.setFailedAt(LocalDateTime.now());
        payment.setFailureReason(reason);
        
        logger.warn("Payment failed: {}", reason);
    }
}
