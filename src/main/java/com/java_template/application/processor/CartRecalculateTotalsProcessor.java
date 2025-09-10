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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CartRecalculateTotalsProcessor - Recalculates cart totals
 * 
 * This processor recalculates cart totals (totalItems and grandTotal) whenever cart lines are modified.
 * Used in transitions: ACTIVATE_CART, ADD_ITEM, UPDATE_ITEM, REMOVE_ITEM
 */
@Component
public class CartRecalculateTotalsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartRecalculateTotalsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CartRecalculateTotalsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart totals recalculation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Cart.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cart entity wrapper")
                .map(this::processCartTotalsLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Cart> entityWithMetadata) {
        Cart cart = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return cart != null && cart.isValid() && technicalId != null;
    }

    /**
     * Main business logic for recalculating cart totals
     */
    private EntityWithMetadata<Cart> processCartTotalsLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Recalculating totals for cart: {}", cart.getCartId());

        // Initialize totals
        int totalItems = 0;
        BigDecimal grandTotal = BigDecimal.ZERO;

        // Calculate totals from cart lines
        if (cart.getLines() != null && !cart.getLines().isEmpty()) {
            for (Cart.CartLine line : cart.getLines()) {
                if (line.getQty() != null && line.getQty() > 0 && 
                    line.getPrice() != null && line.getPrice().compareTo(BigDecimal.ZERO) >= 0) {
                    
                    // Add to total items
                    totalItems += line.getQty();
                    
                    // Add to grand total (price * quantity)
                    BigDecimal lineTotal = line.getPrice().multiply(BigDecimal.valueOf(line.getQty()));
                    grandTotal = grandTotal.add(lineTotal);
                }
            }
        }

        // Update cart totals
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart {} totals recalculated: {} items, total: {}", 
                   cart.getCartId(), totalItems, grandTotal);

        return entityWithMetadata;
    }
}
