package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

@Component
public class CartOpenCheckoutProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartOpenCheckoutProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CartOpenCheckoutProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart open checkout for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract cart entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract cart entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::processOpenCheckout)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null && cart.isValid();
    }

    private Cart processOpenCheckout(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        
        logger.info("Validating cart for checkout: cartId={}", cart.getCartId());

        // Validate cart has items
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            throw new IllegalStateException("Cart must have items to proceed to checkout");
        }

        // Validate all items have valid SKUs and quantities
        for (Cart.CartLine line : cart.getLines()) {
            if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                throw new IllegalStateException("Cart contains line with invalid SKU");
            }
            if (line.getQty() == null || line.getQty() <= 0) {
                throw new IllegalStateException("Cart contains line with invalid quantity for SKU: " + line.getSku());
            }
        }

        // Check product availability for all items
        for (Cart.CartLine line : cart.getLines()) {
            try {
                EntityResponse<Product> productResponse = entityService.findByBusinessId(Product.class, line.getSku());
                Product product = productResponse.getData();
                
                if (product == null) {
                    throw new IllegalStateException("Product not found for SKU: " + line.getSku());
                }

                if (product.getQuantityAvailable() < line.getQty()) {
                    throw new IllegalStateException("Insufficient stock for product " + line.getSku() + 
                        ": requested " + line.getQty() + ", available " + product.getQuantityAvailable());
                }

                logger.info("Stock validation passed for SKU {}: requested={}, available={}", 
                    line.getSku(), line.getQty(), product.getQuantityAvailable());

            } catch (Exception e) {
                logger.error("Failed to validate product availability for SKU {}: {}", line.getSku(), e.getMessage());
                throw new IllegalStateException("Failed to validate product availability: " + e.getMessage());
            }
        }

        // Validate cart totals
        if (cart.getTotalItems() == null || cart.getTotalItems() <= 0) {
            throw new IllegalStateException("Cart total items must be greater than 0");
        }
        if (cart.getGrandTotal() == null || cart.getGrandTotal() <= 0) {
            throw new IllegalStateException("Cart grand total must be greater than 0");
        }

        // Update timestamp
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart validation successful for checkout: cartId={}, totalItems={}, grandTotal={}", 
            cart.getCartId(), cart.getTotalItems(), cart.getGrandTotal());
        
        return cart;
    }
}
