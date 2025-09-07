package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.dto.EntityWithMetadata;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Cart Recalculate Totals Processor
 * 
 * Recalculates cart totals when items are added, updated, or removed.
 * Transitions: CREATE_ON_FIRST_ADD, ADD_ITEM, UPDATE_ITEM, REMOVE_ITEM
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
        logger.info("Processing cart totals recalculation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Cart.class)
            .validate(this::isValidEntityWithMetadata, "Invalid cart entity wrapper")
            .map(this::processCartRecalculation)
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
        return cart != null && cart.isValid() && entityWithMetadata.getId() != null;
    }

    /**
     * Main business logic for cart totals recalculation
     */
    private EntityWithMetadata<Cart> processCartRecalculation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {
        
        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Recalculating totals for cart: {}", cart.getCartId());

        // Recalculate totals
        recalculateTotals(cart);
        
        // Update timestamp
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart totals recalculated for cart: {} - Items: {}, Total: {}", 
            cart.getCartId(), cart.getTotalItems(), cart.getGrandTotal());

        return entityWithMetadata;
    }

    /**
     * Recalculate cart totals based on current lines
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
            // Validate line data
            if (line.getQty() == null || line.getQty() <= 0) {
                logger.warn("Invalid quantity for line: {}", line.getSku());
                continue;
            }
            
            if (line.getPrice() == null || line.getPrice() < 0) {
                logger.warn("Invalid price for line: {}", line.getSku());
                continue;
            }
            
            totalItems += line.getQty();
            grandTotal += line.getPrice() * line.getQty();
        }
        
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(Math.round(grandTotal * 100.0) / 100.0); // Round to 2 decimal places
    }
}
