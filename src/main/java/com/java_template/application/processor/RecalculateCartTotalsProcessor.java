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
 * ABOUTME: Processor for recalculating cart totals including line totals,
 * total items count, and grand total whenever cart lines are modified.
 */
@Component
public class RecalculateCartTotalsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RecalculateCartTotalsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RecalculateCartTotalsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Cart.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cart entity wrapper")
                .map(this::processCartTotalsCalculation)
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
        return cart != null && cart.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Process cart totals calculation logic
     */
    private EntityWithMetadata<Cart> processCartTotalsCalculation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Processing cart totals calculation for cart: {}", cart.getCartId());

        // Calculate line totals
        calculateLineTotals(cart);

        // Calculate total items and grand total
        calculateCartTotals(cart);

        // Update timestamps
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart totals recalculated for cart: {}", cart.getCartId());

        return entityWithMetadata;
    }

    /**
     * Calculate line totals for each cart line
     */
    private void calculateLineTotals(Cart cart) {
        if (cart.getLines() != null) {
            for (Cart.CartLine line : cart.getLines()) {
                if (line.getPrice() != null && line.getQty() != null) {
                    Double lineTotal = line.getPrice() * line.getQty();
                    line.setLineTotal(lineTotal);
                    logger.debug("Calculated line total {} for SKU {}", lineTotal, line.getSku());
                }
            }
        }
    }

    /**
     * Calculate total items count and grand total
     */
    private void calculateCartTotals(Cart cart) {
        if (cart.getLines() != null) {
            // Calculate total items
            int totalItems = cart.getLines().stream()
                    .mapToInt(line -> line.getQty() != null ? line.getQty() : 0)
                    .sum();
            cart.setTotalItems(totalItems);

            // Calculate grand total
            Double grandTotal = cart.getLines().stream()
                    .mapToDouble(line -> line.getLineTotal() != null ? line.getLineTotal() : 0.0)
                    .sum();
            cart.setGrandTotal(grandTotal);

            logger.debug("Calculated cart totals - items: {}, grand total: {}", totalItems, grandTotal);
        } else {
            // Empty cart
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
        }
    }
}
