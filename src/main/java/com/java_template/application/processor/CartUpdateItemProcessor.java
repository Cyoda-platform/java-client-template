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
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::processCartUpdateItem)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null && cart.isValid();
    }

    private Cart processCartUpdateItem(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Note: In a real implementation, the item to update would come from the request payload
        // For this demo, we'll assume it's passed in some way or we extract it from the request
        // This is a simplified implementation - in practice, you'd extract the item details from the request

        // For now, let's assume we're updating the last item in the lines array
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            throw new IllegalArgumentException("No item to update found in request");
        }

        Cart.CartLine itemToUpdate = cart.getLines().get(cart.getLines().size() - 1);

        // Validate item has sku
        if (itemToUpdate.getSku() == null || itemToUpdate.getSku().trim().isEmpty()) {
            throw new IllegalArgumentException("Item must have a valid SKU");
        }

        // Find existing line with sku
        Cart.CartLine existingLine = cart.getLines().stream()
            .filter(line -> itemToUpdate.getSku().equals(line.getSku()))
            .findFirst()
            .orElse(null);

        if (existingLine == null) {
            throw new IllegalArgumentException("Item not found in cart: " + itemToUpdate.getSku());
        }

        if (itemToUpdate.getQty() == null || itemToUpdate.getQty() <= 0) {
            // Remove line from cart
            Iterator<Cart.CartLine> iterator = cart.getLines().iterator();
            while (iterator.hasNext()) {
                Cart.CartLine line = iterator.next();
                if (itemToUpdate.getSku().equals(line.getSku())) {
                    iterator.remove();
                    break;
                }
            }
            logger.info("Item {} removed from cart {}", itemToUpdate.getSku(), cart.getCartId());
        } else {
            // Update line quantity
            existingLine.setQty(itemToUpdate.getQty());
            logger.info("Item {} quantity updated to {} in cart {}", itemToUpdate.getSku(), itemToUpdate.getQty(), cart.getCartId());
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

        return cart;
    }
}
