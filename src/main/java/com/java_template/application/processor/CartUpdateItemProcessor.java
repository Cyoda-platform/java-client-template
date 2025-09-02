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

import java.util.Map;

@Component
public class CartUpdateItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartUpdateItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CartUpdateItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart update item for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract cart entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract cart entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::processUpdateItem)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null && cart.isValid();
    }

    private Cart processUpdateItem(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Extract item details from request data - for now using hardcoded values
        // In a real implementation, this would come from the request payload
        String sku = "SAMPLE_SKU"; // TODO: Extract from request payload
        Integer qty = 2; // TODO: Extract from request payload

        if (sku == null || sku.trim().isEmpty()) {
            throw new IllegalArgumentException("SKU is required for updating item in cart");
        }
        if (qty == null || qty < 0) {
            throw new IllegalArgumentException("Quantity must be 0 or greater");
        }

        logger.info("Updating item in cart: SKU={}, Qty={}", sku, qty);

        // Find line with matching SKU
        Cart.CartLine existingLine = cart.findLineBySkuOrNull(sku);
        
        if (existingLine == null) {
            throw new IllegalArgumentException("Item with SKU " + sku + " not found in cart");
        }

        if (qty == 0) {
            // Remove line from cart
            boolean removed = cart.removeLine(sku);
            if (removed) {
                logger.info("Removed line for SKU {} from cart", sku);
            }
        } else {
            // Update line quantity
            existingLine.setQty(qty);
            existingLine.calculateLineTotal();
            logger.info("Updated line for SKU {}: new qty={}, lineTotal={}", 
                sku, existingLine.getQty(), existingLine.getLineTotal());
        }

        // Recalculate cart totals
        cart.recalculateTotals();

        logger.info("Cart updated successfully: totalItems={}, grandTotal={}", 
            cart.getTotalItems(), cart.getGrandTotal());
        
        return cart;
    }
}
