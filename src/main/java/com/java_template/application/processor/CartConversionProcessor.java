package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.payment.version_1.Payment;
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

@Component
public class CartConversionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartConversionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CartConversionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart conversion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::processCartConversion)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null && cart.isValid();
    }

    private Cart processCartConversion(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Validate cart has guestContact information
        if (cart.getGuestContact() == null) {
            throw new IllegalArgumentException("Cart must have guest contact information");
        }

        if (cart.getGuestContact().getName() == null || cart.getGuestContact().getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Guest name is required");
        }

        if (cart.getGuestContact().getAddress() == null) {
            throw new IllegalArgumentException("Guest address is required");
        }

        Cart.Address address = cart.getGuestContact().getAddress();
        if (address.getLine1() == null || address.getLine1().trim().isEmpty() ||
            address.getCity() == null || address.getCity().trim().isEmpty() ||
            address.getPostcode() == null || address.getPostcode().trim().isEmpty() ||
            address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            throw new IllegalArgumentException("Complete address information is required");
        }

        // Validate payment is in PAID state
        Condition cartIdCondition = Condition.of("$.cartId", "EQUALS", cart.getCartId());
        SearchConditionRequest condition = new SearchConditionRequest();
        condition.setType("group");
        condition.setOperator("AND");
        condition.setConditions(List.of(cartIdCondition));

        Optional<EntityResponse<Payment>> paymentResponse = entityService.getFirstItemByCondition(
            Payment.class, Payment.ENTITY_NAME, Payment.ENTITY_VERSION, condition, true);

        if (paymentResponse.isEmpty()) {
            throw new IllegalArgumentException("No payment found for cart: " + cart.getCartId());
        }

        String paymentState = paymentResponse.get().getMetadata().getState();
        if (!"PAID".equals(paymentState)) {
            throw new IllegalArgumentException("Payment must be in PAID state, current state: " + paymentState);
        }

        // Set updatedAt timestamp
        cart.setUpdatedAt(Instant.now());

        logger.info("Cart {} converted successfully", cart.getCartId());
        return cart;
    }
}
