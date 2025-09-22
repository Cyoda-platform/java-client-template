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
 * RecalculateCartTotalsProcessor - Handles cart totals calculation
 * 
 * This processor is responsible for:
 * - Calculating line totals for each cart item
 * - Calculating total items count
 * - Calculating grand total
 * - Updating timestamps
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
        return cart != null && cart.getCartId() != null && technicalId != null;
    }

    /**
     * Main cart totals calculation logic
     */
    private EntityWithMetadata<Cart> processCartTotalsCalculation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Processing cart totals calculation for cart: {}", cart.getCartId());

        // Calculate line totals
        calculateLineTotals(cart);

        // Calculate total items
        calculateTotalItems(cart);

        // Calculate grand total
        calculateGrandTotal(cart);

        // Update timestamp
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart {} totals calculated successfully - Items: {}, Grand Total: {}", 
                cart.getCartId(), cart.getTotalItems(), cart.getGrandTotal());

        return entityWithMetadata;
    }

    /**
     * Calculate line totals for each cart line
     */
    private void calculateLineTotals(Cart cart) {
        if (cart.getLines() == null) {
            return;
        }

        for (Cart.CartLine line : cart.getLines()) {
            if (line.getPrice() != null && line.getQty() != null) {
                Double lineTotal = line.getPrice() * line.getQty();
                line.setLineTotal(lineTotal);
                logger.debug("Calculated line total for SKU {}: {} x {} = {}", 
                        line.getSku(), line.getPrice(), line.getQty(), lineTotal);
            } else {
                line.setLineTotal(0.0);
                logger.warn("Missing price or quantity for line SKU: {}", line.getSku());
            }
        }
    }

    /**
     * Calculate total items count
     */
    private void calculateTotalItems(Cart cart) {
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            cart.setTotalItems(0);
            return;
        }

        int totalItems = cart.getLines().stream()
                .mapToInt(line -> line.getQty() != null ? line.getQty() : 0)
                .sum();

        cart.setTotalItems(totalItems);
        logger.debug("Calculated total items for cart {}: {}", cart.getCartId(), totalItems);
    }

    /**
     * Calculate grand total
     */
    private void calculateGrandTotal(Cart cart) {
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            cart.setGrandTotal(0.0);
            return;
        }

        double grandTotal = cart.getLines().stream()
                .mapToDouble(line -> line.getLineTotal() != null ? line.getLineTotal() : 0.0)
                .sum();

        cart.setGrandTotal(grandTotal);
        logger.debug("Calculated grand total for cart {}: {}", cart.getCartId(), grandTotal);
    }
}
