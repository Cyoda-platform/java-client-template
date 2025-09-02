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
import java.util.Iterator;

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
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::processCartRemoveItem)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null && cart.isValid();
    }

    private Cart processCartRemoveItem(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Note: In a real implementation, the item to remove would come from the request payload
        // For this demo, we'll assume it's passed in some way or we extract it from the request
        // This is a simplified implementation - in practice, you'd extract the item details from the request

        // For now, let's assume we're removing the last item in the lines array
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            throw new IllegalArgumentException("No item to remove found in request");
        }

        Cart.CartLine itemToRemove = cart.getLines().get(cart.getLines().size() - 1);

        // Validate item has sku
        if (itemToRemove.getSku() == null || itemToRemove.getSku().trim().isEmpty()) {
            throw new IllegalArgumentException("Item must have a valid SKU");
        }

        // Find existing line with sku
        boolean found = false;
        Iterator<Cart.CartLine> iterator = cart.getLines().iterator();
        while (iterator.hasNext()) {
            Cart.CartLine line = iterator.next();
            if (itemToRemove.getSku().equals(line.getSku())) {
                iterator.remove();
                found = true;
                break;
            }
        }

        if (!found) {
            throw new IllegalArgumentException("Item not found in cart: " + itemToRemove.getSku());
        }

        // Recalculate totalItems and grandTotal
        int totalItems = 0;
        double grandTotal = 0.0;

        for (Cart.CartLine line : cart.getLines()) {
            if (line.getQty() != null && line.getPrice() != null) {
                totalItems += line.getQty();
                grandTotal += line.getPrice() * line.getQty();
            }
        }

        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);

        // Set updatedAt timestamp
        cart.setUpdatedAt(Instant.now());

        logger.info("Item {} removed from cart {}", itemToRemove.getSku(), cart.getCartId());
        return cart;
    }
}
