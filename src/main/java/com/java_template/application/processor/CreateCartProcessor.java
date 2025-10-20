package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * ABOUTME: Processor for creating new Cart entities, initializing cart state
 * and setting up initial cart structure for the first item addition.
 */
@Component
public class CreateCartProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateCartProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateCartProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Cart.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cart entity wrapper")
                .map(this::processCartCreation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for Cart
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Cart> entityWithMetadata) {
        Cart cart = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return cart != null && cart.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for cart creation
     */
    private EntityWithMetadata<Cart> processCartCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Processing cart creation for cartId: {}", cart.getCartId());

        // Initialize cart state
        initializeCart(cart);

        // Calculate initial totals
        recalculateTotals(cart);

        logger.info("Cart {} created successfully", cart.getCartId());

        return entityWithMetadata;
    }

    /**
     * Initialize cart with default values
     */
    private void initializeCart(Cart cart) {
        // Set status to ACTIVE (cart is created when first item is added)
        cart.setStatus("ACTIVE");
        
        // Initialize lines if null
        if (cart.getLines() == null) {
            cart.setLines(new ArrayList<>());
        }
        
        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        cart.setCreatedAt(now);
        cart.setUpdatedAt(now);
        
        logger.debug("Cart initialized with status ACTIVE for cartId: {}", cart.getCartId());
    }

    /**
     * Recalculate cart totals
     */
    private void recalculateTotals(Cart cart) {
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            return;
        }

        int totalItems = 0;
        double grandTotal = 0.0;

        for (Cart.CartLine line : cart.getLines()) {
            if (line.getQty() != null && line.getPrice() != null) {
                // Calculate line total
                double lineTotal = line.getPrice() * line.getQty();
                line.setLineTotal(lineTotal);
                
                // Add to cart totals
                totalItems += line.getQty();
                grandTotal += lineTotal;
            }
        }

        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);

        logger.debug("Cart totals recalculated for cartId: {}, items: {}, total: {}", 
                    cart.getCartId(), totalItems, grandTotal);
    }
}
