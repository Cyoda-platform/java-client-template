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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;

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
        
        logger.debug("Recalculating totals for cart: {}", cart.getCartId());

        // Initialize totals
        int totalItems = 0;
        double grandTotal = 0.0;

        // Handle null or empty lines
        if (cart.getLines() == null) {
            cart.setLines(new ArrayList<>());
        }

        // Remove lines with qty <= 0 and calculate totals
        Iterator<Cart.CartLine> iterator = cart.getLines().iterator();
        while (iterator.hasNext()) {
            Cart.CartLine line = iterator.next();
            
            if (line.getQty() == null || line.getQty() <= 0) {
                logger.debug("Removing line with zero or negative quantity: {}", line.getSku());
                iterator.remove();
                continue;
            }

            // Handle negative prices by using 0
            double price = line.getPrice() != null && line.getPrice() >= 0 ? line.getPrice() : 0.0;
            if (line.getPrice() != null && line.getPrice() < 0) {
                logger.warn("Negative price detected for SKU {}, using 0", line.getSku());
                line.setPrice(0.0);
                price = 0.0;
            }

            // Calculate line total
            double lineTotal = price * line.getQty();
            line.setLineTotal(lineTotal);

            // Add to totals
            totalItems += line.getQty();
            grandTotal += lineTotal;
        }

        // Update cart totals
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart {} totals recalculated: {} items, total: {}", 
                   cart.getCartId(), totalItems, grandTotal);

        return cart;
    }
}
