package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

@Component
public class CartCreateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartCreateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CartCreateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract cart entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract cart entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::processCartCreation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null;
    }

    private Cart processCartCreation(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        
        logger.info("Creating new cart with initial setup");

        // Generate unique cartId if not present
        if (cart.getCartId() == null || cart.getCartId().trim().isEmpty()) {
            cart.setCartId("cart_" + UUID.randomUUID().toString().replace("-", ""));
        }

        // Initialize cart with empty state if needed
        if (cart.getLines() == null) {
            cart.setLines(new ArrayList<>());
        }

        // Set initial totals
        if (cart.getTotalItems() == null) {
            cart.setTotalItems(0);
        }
        if (cart.getGrandTotal() == null) {
            cart.setGrandTotal(0.0);
        }

        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        if (cart.getCreatedAt() == null) {
            cart.setCreatedAt(now);
        }
        cart.setUpdatedAt(now);

        // Recalculate totals to ensure consistency
        cart.recalculateTotals();

        logger.info("Cart created successfully with ID: {}", cart.getCartId());
        return cart;
    }
}
