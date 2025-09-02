package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

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
        
        // Extract item data from context (this would come from the request payload)
        // For now, we'll assume the item data is available in the context
        String sku = extractSkuFromContext(context);
        Integer qty = extractQtyFromContext(context);
        
        if (sku == null || qty == null || qty <= 0) {
            throw new IllegalArgumentException("Invalid item data: sku and positive quantity required");
        }
        
        logger.info("Adding item to cart: {} - SKU: {}, Qty: {}", cart.getCartId(), sku, qty);
        
        // Validate product exists and has sufficient stock
        Product product = getProductBySku(sku);
        if (product == null) {
            throw new IllegalArgumentException("Product not found: " + sku);
        }
        
        if (product.getQuantityAvailable() < qty) {
            throw new IllegalArgumentException("Insufficient stock for product: " + sku);
        }
        
        // Initialize lines if null
        if (cart.getLines() == null) {
            cart.setLines(new ArrayList<>());
        }
        
        // Check if item already exists in cart
        Optional<Cart.CartLine> existingLine = cart.getLines().stream()
            .filter(line -> sku.equals(line.getSku()))
            .findFirst();
        
        if (existingLine.isPresent()) {
            // Increment quantity
            Cart.CartLine line = existingLine.get();
            int newQty = line.getQty() + qty;
            
            // Check stock again for new total quantity
            if (product.getQuantityAvailable() < newQty) {
                throw new IllegalArgumentException("Insufficient stock for product: " + sku);
            }
            
            line.setQty(newQty);
        } else {
            // Add new line item
            Cart.CartLine newLine = new Cart.CartLine();
            newLine.setSku(sku);
            newLine.setName(product.getName());
            newLine.setPrice(product.getPrice());
            newLine.setQty(qty);
            cart.getLines().add(newLine);
        }
        
        // Recalculate totals
        recalculateTotals(cart);
        
        // Update timestamps
        cart.setUpdatedAt(Instant.now());
        
        logger.info("Item added to cart successfully: {}", cart.getCartId());
        return cart;
    }

    private String extractSkuFromContext(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        // This would extract from the request payload in a real implementation
        // For now, return a placeholder - this needs to be implemented based on how data is passed
        return null; // TODO: Extract from context
    }

    private Integer extractQtyFromContext(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        // This would extract from the request payload in a real implementation
        // For now, return a placeholder - this needs to be implemented based on how data is passed
        return null; // TODO: Extract from context
    }

    private Product getProductBySku(String sku) {
        try {
            Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("simple");
            condition.setConditions(java.util.List.of(skuCondition));

            Optional<EntityResponse<Product>> productResponse = entityService.getFirstItemByCondition(
                Product.class,
                Product.ENTITY_NAME,
                Product.ENTITY_VERSION,
                condition,
                true
            );

            return productResponse.map(EntityResponse::getData).orElse(null);
        } catch (Exception e) {
            logger.error("Error fetching product by SKU: {}", sku, e);
            return null;
        }
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
