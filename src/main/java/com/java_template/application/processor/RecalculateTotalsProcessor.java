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
 * Processor to recalculate cart totals after item changes
 * Calculates line totals and grand total for the cart
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
                .map(this::recalculateTotals)
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
        return cart != null && cart.isValid() && technicalId != null;
    }

    /**
     * Recalculates cart totals including line totals and grand total
     */
    private EntityWithMetadata<Cart> recalculateTotals(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Recalculating totals for cart: {}", cart.getCartId());

        // Calculate line totals and count items
        int totalItems = 0;
        double grandTotal = 0.0;

        if (cart.getLines() != null) {
            for (Cart.CartLine line : cart.getLines()) {
                if (line.getPrice() != null && line.getQty() != null) {
                    // Calculate line total
                    double lineTotal = line.getPrice() * line.getQty();
                    line.setLineTotal(lineTotal);
                    
                    // Add to cart totals
                    totalItems += line.getQty();
                    grandTotal += lineTotal;
                }
            }
        }

        // Update cart totals
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart {} totals recalculated: {} items, ${}", 
                   cart.getCartId(), totalItems, grandTotal);

        return entityWithMetadata;
    }
}
