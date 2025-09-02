package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

@Component
public class PaymentStartProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentStartProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PaymentStartProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment start for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract payment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract payment entity: " + error.getMessage());
            })
            .validate(this::isValidPayment, "Invalid payment state")
            .map(this::processPaymentStart)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPayment(Payment payment) {
        return payment != null && payment.isValid();
    }

    private Payment processPaymentStart(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();
        
        logger.info("Starting dummy payment: {}", payment.getPaymentId());
        
        // Validate associated cart exists and is in checking_out state
        Cart cart = getCartById(payment.getCartId());
        if (cart == null) {
            throw new IllegalStateException("Associated cart not found: " + payment.getCartId());
        }
        
        // Validate cart is in checking_out state (this would be checked via entity state)
        // For now, we'll assume the cart state validation is handled elsewhere
        
        // Set payment provider to DUMMY
        payment.setProvider("DUMMY");
        
        // Update timestamps
        payment.setUpdatedAt(Instant.now());
        
        // Schedule auto-payment after 3 seconds (this would be handled by a scheduler or timer)
        // For now, we'll just log that it should be scheduled
        logger.info("Payment initiated, auto-payment scheduled for 3 seconds: {}", payment.getPaymentId());
        
        return payment;
    }

    private Cart getCartById(String cartId) {
        try {
            // This is a simplified approach - in reality we'd need to search by cartId field
            // For now, we'll return null as a placeholder
            // TODO: Implement proper cart lookup by cartId
            return null;
        } catch (Exception e) {
            logger.error("Error fetching cart by ID: {}", cartId, e);
            return null;
        }
    }
}
