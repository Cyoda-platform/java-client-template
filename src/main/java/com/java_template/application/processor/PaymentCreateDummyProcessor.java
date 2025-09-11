package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * PaymentCreateDummyProcessor - Creates a dummy payment record in INITIATED state.
 * 
 * Transitions: START_PAYMENT
 * 
 * Business Logic:
 * - Validates cartId references existing Cart
 * - Validates payment amount matches cart total
 * - Sets provider to "DUMMY"
 * - Sets timestamps
 */
@Component
public class PaymentCreateDummyProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCreateDummyProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PaymentCreateDummyProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment creation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Payment.class)
                .validate(this::isValidEntityWithMetadata, "Invalid payment entity wrapper")
                .map(this::processPaymentCreation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Payment> entityWithMetadata) {
        Payment payment = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return payment != null && technicalId != null && 
               payment.getCartId() != null && payment.getAmount() != null;
    }

    /**
     * Main business logic for payment creation
     */
    private EntityWithMetadata<Payment> processPaymentCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Payment> context) {

        EntityWithMetadata<Payment> entityWithMetadata = context.entityResponse();
        Payment payment = entityWithMetadata.entity();

        logger.debug("Creating dummy payment for cart: {}", payment.getCartId());

        // Validate cartId references existing Cart
        try {
            ModelSpec cartModelSpec = new ModelSpec()
                    .withName(Cart.ENTITY_NAME)
                    .withVersion(Cart.ENTITY_VERSION);
            
            EntityWithMetadata<Cart> cartWithMetadata = entityService.findByBusinessId(
                    cartModelSpec,
                    payment.getCartId(),
                    "cartId",
                    Cart.class
            );
            
            Cart cart = cartWithMetadata.entity();
            
            // Validate payment amount matches cart total
            if (!payment.getAmount().equals(cart.getGrandTotal())) {
                throw new IllegalArgumentException(
                    String.format("Payment amount %.2f doesn't match cart total %.2f", 
                                payment.getAmount(), cart.getGrandTotal()));
            }
            
            logger.debug("Cart validation successful: amount {} matches cart total", payment.getAmount());
            
        } catch (Exception e) {
            logger.error("Cart validation failed for cartId: {}", payment.getCartId());
            throw new IllegalArgumentException("Cart not found: " + payment.getCartId(), e);
        }

        // Set payment provider to DUMMY
        payment.setProvider("DUMMY");
        
        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

        logger.info("Dummy payment {} created for cart {} with amount {}", 
                   payment.getPaymentId(), payment.getCartId(), payment.getAmount());

        return entityWithMetadata;
    }
}
