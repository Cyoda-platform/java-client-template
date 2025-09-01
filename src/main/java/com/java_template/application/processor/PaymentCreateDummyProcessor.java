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

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentCreateDummyProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCreateDummyProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Autowired
    private EntityService entityService;

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
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid payment state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Payment entity) {
        return entity != null && entity.isValid();
    }

    private Payment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();
        
        logger.info("Creating dummy payment for cart: {}", payment.getCartId());

        // Validate payment entity
        if (payment == null) {
            logger.error("Payment entity is null");
            throw new IllegalArgumentException("Payment entity cannot be null");
        }

        // Generate unique payment ID if not set
        if (payment.getPaymentId() == null || payment.getPaymentId().trim().isEmpty()) {
            String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            payment.setPaymentId(paymentId);
            logger.info("Generated payment ID: {}", paymentId);
        }

        // Validate required fields
        if (payment.getCartId() == null || payment.getCartId().trim().isEmpty()) {
            logger.error("Cart ID is required for payment creation");
            throw new IllegalArgumentException("Cart ID is required");
        }

        if (payment.getAmount() == null || payment.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            logger.error("Valid payment amount is required");
            throw new IllegalArgumentException("Valid payment amount is required");
        }

        // Set payment provider to DUMMY
        payment.setProvider("DUMMY");
        
        // Set timestamps
        if (payment.getCreatedAt() == null) {
            payment.setCreatedAt(LocalDateTime.now());
        }
        payment.setUpdatedAt(LocalDateTime.now());

        logger.info("Payment created with ID: {}, amount: {}, for cart: {}", 
                   payment.getPaymentId(), payment.getAmount(), payment.getCartId());

        // Schedule AUTO_MARK_PAID transition after 3 seconds
        scheduleAutoMarkPaid(payment.getPaymentId());

        return payment;
    }

    /**
     * Schedule the AUTO_MARK_PAID transition after 3 seconds for demo purposes.
     */
    private void scheduleAutoMarkPaid(String paymentId) {
        scheduler.schedule(() -> {
            try {
                logger.info("Auto-marking payment as paid: {}", paymentId);
                
                // Find the payment entity
                com.java_template.common.util.SearchConditionRequest condition =
                    com.java_template.common.util.SearchConditionRequest.group("and",
                        com.java_template.common.util.Condition.of("paymentId", "equals", paymentId));
                
                var paymentResponse = entityService.getFirstItemByCondition(Payment.class, condition, false);
                
                if (paymentResponse.isPresent()) {
                    Payment payment = paymentResponse.get().getData();
                    UUID entityId = paymentResponse.get().getMetadata().getId();
                    String currentState = paymentResponse.get().getMetadata().getState();
                    
                    // Only auto-mark if still in INITIATED state
                    if ("INITIATED".equals(currentState)) {
                        payment.setUpdatedAt(LocalDateTime.now());
                        
                        // Update with AUTO_MARK_PAID transition
                        entityService.update(entityId, payment, "AUTO_MARK_PAID");
                        
                        logger.info("Payment auto-marked as paid: {}", paymentId);
                    } else {
                        logger.info("Payment {} is no longer in INITIATED state ({}), skipping auto-mark", 
                                   paymentId, currentState);
                    }
                } else {
                    logger.warn("Payment not found for auto-mark: {}", paymentId);
                }
                
            } catch (Exception e) {
                logger.error("Error auto-marking payment as paid: {}", paymentId, e);
            }
        }, 3, TimeUnit.SECONDS);
        
        logger.info("Scheduled AUTO_MARK_PAID for payment {} in 3 seconds", paymentId);
    }
}
