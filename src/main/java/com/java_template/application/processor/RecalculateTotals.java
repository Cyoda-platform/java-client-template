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
 * Processor to recalculate cart totals when items are added/removed/modified
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
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Cart> entityWithMetadata) {
        Cart cart = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return cart != null && cart.isValid() && technicalId != null;
    }

    /**
     * Recalculates cart totals based on current lines
     */
    private EntityWithMetadata<Cart> recalculateCartTotals(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Recalculating totals for cart: {}", cart.getCartId());

        // Calculate totals from lines
        if (cart.getLines() != null) {
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
                }
            }

            // Update cart totals
            cart.setTotalItems(totalItems);
            cart.setGrandTotal(grandTotal);
        } else {
            // No lines, zero totals
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
        }

        // Update timestamp
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart {} totals recalculated: {} items, ${}", 
                   cart.getCartId(), cart.getTotalItems(), cart.getGrandTotal());

        return entityWithMetadata;
    }
}
