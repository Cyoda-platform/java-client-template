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

import java.util.ArrayList;
import java.util.Map;

@Component
public class CartAddItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartAddItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CartAddItemProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart add item for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract cart entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract cart entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::processAddItem)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null && cart.isValid();
    }

    private Cart processAddItem(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Extract item details from request data - for now using hardcoded values
        // In a real implementation, this would come from the request payload
        String sku = "SAMPLE_SKU"; // TODO: Extract from request payload
        Integer qty = 1; // TODO: Extract from request payload

        if (sku == null || sku.trim().isEmpty()) {
            throw new IllegalArgumentException("SKU is required for adding item to cart");
        }
        if (qty == null || qty <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        logger.info("Adding item to cart: SKU={}, Qty={}", sku, qty);

        // Initialize lines if null
        if (cart.getLines() == null) {
            cart.setLines(new ArrayList<>());
        }

        // Find existing line with matching SKU
        Cart.CartLine existingLine = cart.findLineBySkuOrNull(sku);

        if (existingLine != null) {
            // Increment quantity of existing line
            existingLine.setQty(existingLine.getQty() + qty);
            existingLine.calculateLineTotal();
            logger.info("Updated existing line for SKU {}: new qty={}", sku, existingLine.getQty());
        } else {
            // Get product details by SKU to create new line
            try {
                EntityResponse<Product> productResponse = entityService.findByBusinessId(Product.class, sku);
                Product product = productResponse.getData();
                
                if (product == null) {
                    throw new IllegalArgumentException("Product not found for SKU: " + sku);
                }

                // Check product availability
                if (product.getQuantityAvailable() < qty) {
                    throw new IllegalArgumentException("Insufficient stock for product " + sku + 
                        ": requested " + qty + ", available " + product.getQuantityAvailable());
                }

                // Create new cart line
                Cart.CartLine newLine = new Cart.CartLine(sku, product.getName(), product.getPrice(), qty);
                cart.getLines().add(newLine);
                logger.info("Added new line for SKU {}: name={}, price={}, qty={}", 
                    sku, product.getName(), product.getPrice(), qty);

            } catch (Exception e) {
                logger.error("Failed to get product details for SKU {}: {}", sku, e.getMessage());
                throw new IllegalArgumentException("Failed to add item to cart: " + e.getMessage());
            }
        }

        // Recalculate cart totals
        cart.recalculateTotals();

        logger.info("Cart updated successfully: totalItems={}, grandTotal={}", 
            cart.getTotalItems(), cart.getGrandTotal());
        
        return cart;
    }
}
