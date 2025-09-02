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
import java.util.Optional;

@Component
public class CartRemoveItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartRemoveItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CartRemoveItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart remove item for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract cart entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract cart entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::processRemoveItem)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null && cart.isValid();
    }

    private Cart processRemoveItem(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        
        // Extract item data from context
        String sku = extractSkuFromContext(context);
        
        if (sku == null || sku.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid item data: sku required");
        }
        
        logger.info("Removing item from cart: {} - SKU: {}", cart.getCartId(), sku);
        
        // Find and remove the line item
        Optional<Cart.CartLine> existingLine = cart.getLines().stream()
            .filter(line -> sku.equals(line.getSku()))
            .findFirst();
        
        if (!existingLine.isPresent()) {
            throw new IllegalArgumentException("Item not found in cart: " + sku);
        }
        
        cart.getLines().remove(existingLine.get());
        
        // Recalculate totals
        recalculateTotals(cart);
        
        // Update timestamps
        cart.setUpdatedAt(Instant.now());
        
        logger.info("Item removed from cart successfully: {}", cart.getCartId());
        return cart;
    }

    private String extractSkuFromContext(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        // TODO: Extract from context - placeholder implementation
        return null;
    }

    private void recalculateTotals(Cart cart) {
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            cart.setTotalItems(0);
            cart.setGrandTotal(0.0);
            return;
        }

        int totalItems = 0;
        double grandTotal = 0.0;

        for (Cart.CartLine line : cart.getLines()) {
            if (line.getQty() != null && line.getPrice() != null) {
                totalItems += line.getQty();
                grandTotal += line.getQty() * line.getPrice();
            }
        }

        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);
    }
}
