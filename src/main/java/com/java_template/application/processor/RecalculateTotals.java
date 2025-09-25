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

/**
 * Processor to recalculate cart totals when items are added, removed, or quantities changed.
 * Updates totalItems and grandTotal based on current cart lines.
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
                .map(this::recalculateCartTotals)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that the entity wrapper contains a valid cart
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Cart> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or Cart entity is null");
            return false;
        }

        Cart cart = entityWithMetadata.entity();
        if (!cart.isValid()) {
            logger.error("Cart entity validation failed for cartId: {}", cart.getCartId());
            return false;
        }

        return true;
    }

    /**
     * Recalculates cart totals based on current line items
     */
    private EntityWithMetadata<Cart> recalculateCartTotals(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {
        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();
        logger.info("Recalculating totals for cart: {}", cart.getCartId());

        // Calculate totals from lines
        int totalItems = 0;
        double grandTotal = 0.0;

        if (cart.getLines() != null) {
            for (Cart.CartLine line : cart.getLines()) {
                if (line.getQty() != null && line.getPrice() != null) {
                    totalItems += line.getQty();
                    grandTotal += line.getQty() * line.getPrice();
                }
            }
        }

        // Update cart totals
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);

        logger.info("Cart {} totals updated - Items: {}, Grand Total: {}",
                   cart.getCartId(), totalItems, grandTotal);

        return entityWithMetadata;
    }
}
