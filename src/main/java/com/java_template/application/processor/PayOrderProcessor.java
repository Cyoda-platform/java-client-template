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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ABOUTME: PayOrderProcessor handles payment processing and transition from Submitted to Paid state,
 * updating payment status and timestamps.
 */
@Component
public class PayOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PayOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PayOrderProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processPayOrder)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or Order entity is null");
            return false;
        }

        Order order = entityWithMetadata.entity();
        
        // Validate order is in correct state for payment
        String currentState = entityWithMetadata.metadata().getState();
        if (!"Submitted".equals(currentState)) {
            logger.error("Order is not in Submitted state for payment. Current state: {}, orderId: {}", 
                        currentState, order.getOrderId());
            return false;
        }

        // Validate payment information exists
        if (order.getPayment() == null) {
            logger.error("Payment information is missing for orderId: {}", order.getOrderId());
            return false;
        }

        // Validate payment amount matches order total
        Double orderTotal = order.getOrderTotal();
        Double paymentAmount = order.getPayment().getAmount();
        if (paymentAmount == null || !paymentAmount.equals(orderTotal)) {
            logger.error("Payment amount {} does not match order total {} for orderId: {}", 
                        paymentAmount, orderTotal, order.getOrderId());
            return false;
        }

        return true;
    }

    private EntityWithMetadata<Order> processPayOrder(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {
        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();
        logger.info("Processing payment for order with orderId: {}", order.getOrderId());

        try {
            Order.Payment payment = order.getPayment();
            
            // Generate transaction reference if not present
            if (payment.getTransactionRef() == null || payment.getTransactionRef().trim().isEmpty()) {
                payment.setTransactionRef("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }

            // Update payment status to captured (simulating successful payment)
            payment.setStatus("captured");
            
            // Set payment timestamps
            if (payment.getTimestamps() == null) {
                payment.setTimestamps(new Order.PaymentTimestamps());
            }
            
            LocalDateTime now = LocalDateTime.now();
            payment.getTimestamps().setAuthorized(now);
            payment.getTimestamps().setCaptured(now);

            // Set default currency if not present
            if (payment.getCurrency() == null || payment.getCurrency().trim().isEmpty()) {
                payment.setCurrency("USD");
            }

            // Set default payment method if not present
            if (payment.getMethod() == null || payment.getMethod().trim().isEmpty()) {
                payment.setMethod("credit_card");
            }

            logger.info("Payment processed successfully - orderId: {}, transactionRef: {}, amount: {}", 
                       order.getOrderId(), payment.getTransactionRef(), payment.getAmount());

            return new EntityWithMetadata<>(order, entityWithMetadata.metadata());

        } catch (Exception e) {
            logger.error("Error processing payment for orderId: {}", order.getOrderId(), e);
            
            // Update payment status to failed
            if (order.getPayment() != null) {
                order.getPayment().setStatus("failed");
                if (order.getPayment().getTimestamps() != null) {
                    order.getPayment().getTimestamps().setFailed(LocalDateTime.now());
                }
            }
            
            throw new RuntimeException("Failed to process payment: " + e.getMessage(), e);
        }
    }
}
