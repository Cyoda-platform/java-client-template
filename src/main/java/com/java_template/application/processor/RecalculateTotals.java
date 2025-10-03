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

/**
 * ABOUTME: Cart processor that recalculates totals for cart line items
 * including individual line totals, total item count, and grand total.
 */
@Component
public class RecalculateTotals implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RecalculateTotals.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RecalculateTotals(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart totals recalculation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Cart.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cart entity wrapper")
                .map(this::processCartTotals)
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
        return cart != null && cart.isValid() && technicalId != null;
    }

    /**
     * Main business logic for recalculating cart totals
     */
    private EntityWithMetadata<Cart> processCartTotals(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Recalculating totals for cart: {}", cart.getCartId());

        // Recalculate line totals and overall totals
        recalculateCartTotals(cart);

        // Update timestamp
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart {} totals recalculated - Total Items: {}, Grand Total: {}", 
                   cart.getCartId(), cart.getTotalItems(), cart.getGrandTotal());

        return entityWithMetadata;
    }

    /**
     * Recalculates all cart totals including line totals, item count, and grand total
     */
    private void recalculateCartTotals(Cart cart) {
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            // Empty cart
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            return;
        }

        int totalItems = 0;
        double grandTotal = 0.0;

        // Calculate line totals and accumulate totals
        for (Cart.CartLine line : cart.getLines()) {
            if (line.getPrice() != null && line.getQty() != null && line.getQty() > 0) {
                // Calculate line total
                double lineTotal = line.getPrice() * line.getQty();
                line.setLineTotal(lineTotal);

                // Accumulate totals
                totalItems += line.getQty();
                grandTotal += lineTotal;
            } else {
                // Invalid line - set to zero
                line.setLineTotal(0.0);
            }
        }

        // Update cart totals
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);
    }
}
