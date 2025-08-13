package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
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

@Component
public class CartCheckoutProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartCheckoutProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CartCheckoutProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart checkout for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart state for checkout")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        if (cart == null) {
            logger.error("Cart entity is null");
            return false;
        }
        if (!"active".equalsIgnoreCase(cart.getStatus())) {
            logger.error("Cart status is not active: {}", cart.getStatus());
            return false;
        }
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            logger.error("Cart has no items");
            return false;
        }
        return true;
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        // Deduct stock for each CartItem's product, update Cart status to CHECKED_OUT
        // Business logic:
        // 1. Verify stock availability for each item
        // 2. If stock insufficient, log error and possibly throw exception or handle failure
        // 3. If stock sufficient, deduct stock quantities
        // 4. Update Cart status to CHECKED_OUT
        // 5. Persist changes as needed (handled by framework)

        // TODO: Implement detailed stock checks and deduction logic

        // For now, simulate status update
        cart.setStatus("checked_out");
        logger.info("Cart {} checked out successfully", cart.getCartId());

        return cart;
    }
}
