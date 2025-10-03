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
 * ABOUTME: Processor that recalculates cart totals including total items count and grand total
 * based on cart lines. Updates the cart entity with calculated values.
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

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Cart> entityWithMetadata) {
        return entityWithMetadata != null && 
               entityWithMetadata.entity() != null && 
               entityWithMetadata.entity().isValid();
    }

    private EntityWithMetadata<Cart> recalculateCartTotals(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Recalculating totals for cart: {}", cart.getCartId());

        // Calculate total items and grand total
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

        // Update cart with calculated values
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);

        logger.info("Cart {} totals recalculated: {} items, ${}",
                   cart.getCartId(), totalItems, grandTotal);

        return entityWithMetadata;
    }
}
