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
 * ABOUTME: Processor for recalculating Cart totals after item additions,
 * decrements, or removals, ensuring cart totals are always accurate.
 */
@Component
public class RecalculateTotalsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RecalculateTotalsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RecalculateTotalsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Cart.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cart entity wrapper")
                .map(this::processTotalsRecalculation)
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
     * Main business logic for totals recalculation
     */
    private EntityWithMetadata<Cart> processTotalsRecalculation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Processing totals recalculation for cartId: {}", cart.getCartId());

        // Remove lines with zero or negative quantities
        cleanupCartLines(cart);

        // Recalculate all totals
        recalculateTotals(cart);

        // Update timestamp
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart totals recalculated for cartId: {}, items: {}, total: {}", 
                   cart.getCartId(), cart.getTotalItems(), cart.getGrandTotal());

        return entityWithMetadata;
    }

    /**
     * Remove cart lines with zero or negative quantities
     */
    private void cleanupCartLines(Cart cart) {
        if (cart.getLines() == null) {
            return;
        }

        int originalSize = cart.getLines().size();
        cart.getLines().removeIf(line -> line.getQty() == null || line.getQty() <= 0);
        
        int removedCount = originalSize - cart.getLines().size();
        if (removedCount > 0) {
            logger.debug("Removed {} empty lines from cart: {}", removedCount, cart.getCartId());
        }
    }

    /**
     * Recalculate cart totals and line totals
     */
    private void recalculateTotals(Cart cart) {
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            logger.debug("Cart {} is empty, totals set to zero", cart.getCartId());
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
                
                logger.debug("Line calculated: SKU={}, qty={}, price={}, total={}", 
                           line.getSku(), line.getQty(), line.getPrice(), lineTotal);
            } else {
                logger.warn("Invalid line data for SKU: {}, qty: {}, price: {}", 
                          line.getSku(), line.getQty(), line.getPrice());
            }
        }

        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);

        logger.debug("Cart totals calculated: cartId={}, totalItems={}, grandTotal={}", 
                    cart.getCartId(), totalItems, grandTotal);
    }
}
