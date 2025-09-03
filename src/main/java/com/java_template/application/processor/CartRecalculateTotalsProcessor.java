package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class CartRecalculateTotalsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartRecalculateTotalsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CartRecalculateTotalsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart totals recalculation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract cart entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract cart entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::recalculateCartTotals)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null && cart.getCartId() != null && !cart.getCartId().trim().isEmpty();
    }

    private Cart recalculateCartTotals(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        
        logger.info("Recalculating totals for cart: {}", cart.getCartId());
        
        // CRITICAL: The cart entity already contains all the data we need
        // Never extract from request payload - use cart getters directly
        
        if (cart.getLines() == null) {
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            cart.setUpdatedAt(Instant.now());
            logger.info("Cart {} has no lines, set totals to zero", cart.getCartId());
            return cart;
        }
        
        int totalItems = 0;
        double grandTotal = 0.0;
        
        for (Cart.CartLine line : cart.getLines()) {
            if (line != null && line.getQty() != null && line.getPrice() != null) {
                // Validate line has required fields
                if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                    logger.warn("Cart line missing SKU, skipping");
                    continue;
                }
                if (line.getName() == null || line.getName().trim().isEmpty()) {
                    logger.warn("Cart line missing name for SKU {}, skipping", line.getSku());
                    continue;
                }
                if (line.getQty() <= 0) {
                    logger.warn("Cart line has invalid quantity {} for SKU {}, skipping", line.getQty(), line.getSku());
                    continue;
                }
                if (line.getPrice() < 0) {
                    logger.warn("Cart line has invalid price {} for SKU {}, skipping", line.getPrice(), line.getSku());
                    continue;
                }
                
                // Calculate line total and add to totals
                double lineTotal = line.getPrice() * line.getQty();
                totalItems += line.getQty();
                grandTotal += lineTotal;
                
                logger.debug("Added line: SKU={}, qty={}, price={}, lineTotal={}", 
                    line.getSku(), line.getQty(), line.getPrice(), lineTotal);
            }
        }
        
        // Update cart totals
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);
        cart.setUpdatedAt(Instant.now());
        
        logger.info("Cart {} totals recalculated: totalItems={}, grandTotal={}", 
            cart.getCartId(), totalItems, grandTotal);
        
        return cart;
    }
}
