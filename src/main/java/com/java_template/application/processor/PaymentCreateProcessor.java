package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class PaymentCreateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCreateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PaymentCreateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract payment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract payment entity: " + error.getMessage());
            })
            .validate(this::isValidPayment, "Invalid payment state")
            .map(this::processPaymentCreation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPayment(Payment payment) {
        return payment != null;
    }

    private Payment processPaymentCreation(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();

        // Extract payment details from request data - for now using hardcoded values
        // In a real implementation, this would come from the request payload
        String cartId = "cart_sample"; // TODO: Extract from request payload

        if (cartId == null || cartId.trim().isEmpty()) {
            throw new IllegalArgumentException("Cart ID is required for payment creation");
        }

        logger.info("Creating payment for cart: {}", cartId);

        // Generate unique paymentId if not present
        if (payment.getPaymentId() == null || payment.getPaymentId().trim().isEmpty()) {
            payment.setPaymentId("pay_" + UUID.randomUUID().toString().replace("-", ""));
        }

        // Set cartId reference
        payment.setCartId(cartId);

        // Set provider to DUMMY
        payment.setProvider("DUMMY");

        // Get cart to determine amount
        try {
            EntityResponse<Cart> cartResponse = entityService.findByBusinessId(Cart.class, cartId);
            Cart cart = cartResponse.getData();
            
            if (cart == null) {
                throw new IllegalArgumentException("Cart not found for ID: " + cartId);
            }

            if (cart.getGrandTotal() == null || cart.getGrandTotal() <= 0) {
                throw new IllegalArgumentException("Cart must have a valid grand total for payment");
            }

            // Set amount from cart grandTotal
            payment.setAmount(cart.getGrandTotal());

            logger.info("Payment amount set from cart: amount={}", payment.getAmount());

        } catch (Exception e) {
            logger.error("Failed to get cart details for payment creation: {}", e.getMessage());
            throw new IllegalArgumentException("Failed to create payment: " + e.getMessage());
        }

        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        if (payment.getCreatedAt() == null) {
            payment.setCreatedAt(now);
        }
        payment.setUpdatedAt(now);

        logger.info("Payment created successfully: paymentId={}, cartId={}, amount={}, provider={}", 
            payment.getPaymentId(), payment.getCartId(), payment.getAmount(), payment.getProvider());
        
        return payment;
    }
}
