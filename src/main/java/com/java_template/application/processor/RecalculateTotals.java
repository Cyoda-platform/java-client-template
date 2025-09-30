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
 * Processor to recalculate cart totals when items are added, updated, or removed
 * Automatically calculates line totals, total items count, and grand total
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
     * Main business logic for recalculating cart totals
     */
    private EntityWithMetadata<Cart> processCartTotals(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Recalculating totals for cart: {}", cart.getCartId());

        // Calculate line totals and overall totals
        calculateLineTotals(cart);
        calculateCartTotals(cart);

        // Update timestamp
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart {} totals recalculated - Items: {}, Grand Total: {}", 
                   cart.getCartId(), cart.getTotalItems(), cart.getGrandTotal());

        return entityWithMetadata;
    }

    /**
     * Calculate totals for each line item
     */
    private void calculateLineTotals(Cart cart) {
        if (cart.getLines() != null) {
            for (Cart.CartLine line : cart.getLines()) {
                if (line.getPrice() != null && line.getQty() != null) {
                    Double lineTotal = line.getPrice() * line.getQty();
                    line.setLineTotal(lineTotal);
                    logger.debug("Line total calculated for SKU {}: {} x {} = {}", 
                               line.getSku(), line.getPrice(), line.getQty(), lineTotal);
                }
            }
        }
    }

    /**
     * Calculate overall cart totals
     */
    private void calculateCartTotals(Cart cart) {
        if (cart.getLines() != null) {
            // Calculate total items count
            int totalItems = cart.getLines().stream()
                    .mapToInt(line -> line.getQty() != null ? line.getQty() : 0)
                    .sum();
            cart.setTotalItems(totalItems);

            // Calculate grand total
            double grandTotal = cart.getLines().stream()
                    .mapToDouble(line -> line.getLineTotal() != null ? line.getLineTotal() : 0.0)
                    .sum();
            cart.setGrandTotal(grandTotal);

            logger.debug("Cart totals calculated - Total Items: {}, Grand Total: {}", 
                        totalItems, grandTotal);
        } else {
            // Empty cart
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
        }
    }
}
