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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Processor to recalculate cart totals when items are added, removed, or quantities changed
 * Used in Cart workflow transitions: add_item, decrement_item, remove_item, create_on_first_add
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
        logger.info("Processing {} for request: {}", className, request.getId());

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
     * Main business logic to recalculate cart totals
     */
    private EntityWithMetadata<Cart> processCartTotals(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Recalculating totals for cart: {}", cart.getCartId());

        // Recalculate line totals and cart totals
        recalculateLineTotals(cart);
        recalculateCartTotals(cart);

        // Update timestamp
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart {} totals recalculated successfully. Total items: {}, Grand total: {}", 
                   cart.getCartId(), cart.getTotalItems(), cart.getGrandTotal());

        return entityWithMetadata;
    }

    /**
     * Recalculate totals for each line item
     */
    private void recalculateLineTotals(Cart cart) {
        if (cart.getLines() != null) {
            for (Cart.CartLine line : cart.getLines()) {
                if (line.getPrice() != null && line.getQty() != null) {
                    Double lineTotal = line.getPrice() * line.getQty();
                    line.setLineTotal(lineTotal);
                    logger.debug("Line {} total: {} (price: {} x qty: {})", 
                               line.getSku(), lineTotal, line.getPrice(), line.getQty());
                }
            }
        }
    }

    /**
     * Recalculate cart-level totals
     */
    private void recalculateCartTotals(Cart cart) {
        if (cart.getLines() != null) {
            // Calculate total items (sum of all quantities)
            int totalItems = cart.getLines().stream()
                    .mapToInt(line -> line.getQty() != null ? line.getQty() : 0)
                    .sum();
            cart.setTotalItems(totalItems);

            // Calculate grand total (sum of all line totals)
            double grandTotal = cart.getLines().stream()
                    .mapToDouble(line -> line.getLineTotal() != null ? line.getLineTotal() : 0.0)
                    .sum();
            cart.setGrandTotal(grandTotal);
        } else {
            // Empty cart
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
        }
    }
}
