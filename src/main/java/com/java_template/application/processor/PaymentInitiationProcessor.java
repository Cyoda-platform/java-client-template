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
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentInitiationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentInitiationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PaymentInitiationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment initiation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Payment.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidPayment, "Invalid payment state")
            .map(this::processPaymentInitiation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPayment(Payment payment) {
        return payment != null;
    }

    private Payment processPaymentInitiation(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment payment = context.entity();

        // Generate unique paymentId if not set
        if (payment.getPaymentId() == null || payment.getPaymentId().trim().isEmpty()) {
            payment.setPaymentId("pay-" + UUID.randomUUID().toString());
        }

        // Validate cartId exists and cart is in CHECKING_OUT state
        if (payment.getCartId() == null || payment.getCartId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment must have a valid cart ID");
        }

        Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", payment.getCartId());
        SearchConditionRequest condition = new SearchConditionRequest();
        condition.setType("group");
        condition.setOperator("AND");
        condition.setConditions(List.of(cartIdCondition));

        Optional<EntityResponse<Cart>> cartResponse = entityService.getFirstItemByCondition(
            Cart.class, Cart.ENTITY_NAME, Cart.ENTITY_VERSION, condition, true);

        if (cartResponse.isEmpty()) {
            throw new IllegalArgumentException("Cart not found: " + payment.getCartId());
        }

        Cart cart = cartResponse.get().getData();
        String cartState = cartResponse.get().getMetadata().getState();

        if (!"CHECKING_OUT".equals(cartState)) {
            throw new IllegalArgumentException("Cart must be in CHECKING_OUT state, current state: " + cartState);
        }

        // Get cart total amount and set payment amount
        payment.setAmount(cart.getGrandTotal());

        // Set provider to "DUMMY"
        payment.setProvider("DUMMY");

        // Set timestamps
        Instant now = Instant.now();
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

        // Note: In a real implementation, we would schedule auto-approval after 3 seconds
        // This would typically be done through a scheduler or message queue
        logger.info("Payment {} initiated for cart {} with amount {}", 
                   payment.getPaymentId(), payment.getCartId(), payment.getAmount());
        logger.info("Auto-approval will be triggered after 3 seconds");

        return payment;
    }
}
