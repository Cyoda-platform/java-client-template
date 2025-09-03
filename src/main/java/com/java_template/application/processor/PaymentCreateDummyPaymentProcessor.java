package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Component
public class PaymentCreateDummyPaymentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentCreateDummyPaymentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PaymentCreateDummyPaymentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment create dummy for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract payment entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract payment entity: " + error.getMessage());
            })
            .validate(this::isValidPayment, "Invalid payment state")
            .map(this::processPaymentLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPayment(Payment payment) {
        return payment != null && payment.isValid();
    }

    private Payment processPaymentLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();

        try {
            // Validate cart exists and is in CHECKING_OUT state
            var cartResponse = entityService.findByField(
                Cart.class,
                Cart.ENTITY_NAME,
                Cart.ENTITY_VERSION,
                "cartId",
                payment.getCartId()
            );

            if (cartResponse.isEmpty()) {
                throw new RuntimeException("Cart not found with ID: " + payment.getCartId());
            }

            Cart cart = cartResponse.get(0).getData();
            String cartState = cartResponse.get(0).getMetadata().getState();

            if (!"CHECKING_OUT".equals(cartState)) {
                throw new RuntimeException("Cart must be in CHECKING_OUT state, current state: " + cartState);
            }

            // Validate payment amount matches cart total
            if (!payment.getAmount().equals(cart.getGrandTotal())) {
                throw new RuntimeException("Payment amount must match cart total. Payment: " + 
                    payment.getAmount() + ", Cart: " + cart.getGrandTotal());
            }

            // Set payment provider to DUMMY
            payment.setProvider("DUMMY");

            // Generate payment ID if not set
            if (payment.getPaymentId() == null || payment.getPaymentId().trim().isEmpty()) {
                payment.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }

            logger.info("Dummy payment created with ID: {}", payment.getPaymentId());
            return payment;

        } catch (Exception e) {
            logger.error("Error processing payment creation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create dummy payment: " + e.getMessage(), e);
        }
    }
}
