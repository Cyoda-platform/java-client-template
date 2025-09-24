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
 * Processor for recalculating cart totals
 * Updates totalItems and grandTotal based on cart lines
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
                .map(this::recalculateTotalsWithContext)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validate the entity wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Cart> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or entity is null");
            return false;
        }

        Cart cart = entityWithMetadata.entity();
        if (!cart.isValid()) {
            logger.error("Cart entity is not valid: {}", cart);
            return false;
        }

        return true;
    }

    /**
     * Recalculate cart totals with context
     */
    private EntityWithMetadata<Cart> recalculateTotalsWithContext(ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {
        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();
        logger.info("Recalculating totals for cart: {}", cart.getCartId());

        try {
            // Calculate line totals and update each line
            if (cart.getLines() != null) {
                for (Cart.CartLine line : cart.getLines()) {
                    if (line.getPrice() != null && line.getQty() != null) {
                        double lineTotal = line.getPrice() * line.getQty();
                        line.setLineTotal(lineTotal);
                    }
                }
            }

            // Calculate total items
            int totalItems = 0;
            if (cart.getLines() != null) {
                for (Cart.CartLine line : cart.getLines()) {
                    if (line.getQty() != null) {
                        totalItems += line.getQty();
                    }
                }
            }
            cart.setTotalItems(totalItems);

            // Calculate grand total
            double grandTotal = 0.0;
            if (cart.getLines() != null) {
                for (Cart.CartLine line : cart.getLines()) {
                    if (line.getLineTotal() != null) {
                        grandTotal += line.getLineTotal();
                    }
                }
            }
            cart.setGrandTotal(grandTotal);

            // Update timestamp
            cart.setUpdatedAt(LocalDateTime.now());

            logger.info("Updated cart {} - totalItems: {}, grandTotal: {}", 
                       cart.getCartId(), totalItems, grandTotal);
            
            return entityWithMetadata;

        } catch (Exception e) {
            logger.error("Error recalculating totals for cart: {}", cart.getCartId(), e);
            throw new RuntimeException("Failed to recalculate cart totals", e);
        }
    }
}
