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
 * ABOUTME: Processor for recalculating cart totals including line totals, total items count,
 * and grand total whenever cart items are added, updated, or removed.
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
        logger.info("Processing cart totals recalculation for request: {}", request.getId());

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
     * Validates the EntityWithMetadata wrapper for Cart
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Cart> entityWithMetadata) {
        Cart cart = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return cart != null && cart.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for recalculating cart totals
     */
    private EntityWithMetadata<Cart> processCartTotalsLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Recalculating totals for cart: {}", cart.getCartId());

        // Calculate line totals and update each line
        if (cart.getLines() != null) {
            for (Cart.CartLine line : cart.getLines()) {
                if (line.getPrice() != null && line.getQty() != null) {
                    Double lineTotal = line.getPrice() * line.getQty();
                    line.setLineTotal(lineTotal);
                    logger.debug("Updated line total for SKU {}: {} x {} = {}", 
                               line.getSku(), line.getPrice(), line.getQty(), lineTotal);
                }
            }

            // Calculate total items count
            int totalItems = cart.getLines().stream()
                    .mapToInt(line -> line.getQty() != null ? line.getQty() : 0)
                    .sum();
            cart.setTotalItems(totalItems);

            // Calculate grand total
            Double grandTotal = cart.getLines().stream()
                    .mapToDouble(line -> line.getLineTotal() != null ? line.getLineTotal() : 0.0)
                    .sum();
            cart.setGrandTotal(grandTotal);

            logger.info("Cart {} totals recalculated: {} items, grand total: {}", 
                       cart.getCartId(), totalItems, grandTotal);
        } else {
            // Empty cart
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            logger.info("Cart {} is empty, totals reset to zero", cart.getCartId());
        }

        // Update timestamp
        cart.setUpdatedAt(LocalDateTime.now());

        return entityWithMetadata;
    }
}
