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
import java.util.Optional;

@Component
public class CartUpdateItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartUpdateItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CartUpdateItemProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
        
        // Extract item data from context
        String sku = extractSkuFromContext(context);
        Integer newQty = extractQtyFromContext(context);
        
        if (sku == null || newQty == null || newQty < 0) {
            throw new IllegalArgumentException("Invalid item data: sku and non-negative quantity required");
        }
        
        logger.info("Updating item in cart: {} - SKU: {}, New Qty: {}", cart.getCartId(), sku, newQty);
        
        // Find existing line item
        Optional<Cart.CartLine> existingLine = cart.getLines().stream()
            .filter(line -> sku.equals(line.getSku()))
            .findFirst();
        
        if (!existingLine.isPresent()) {
            throw new IllegalArgumentException("Item not found in cart: " + sku);
        }
        
        Cart.CartLine line = existingLine.get();
        
        if (newQty > 0) {
            // Update quantity - validate stock first
            Product product = getProductBySku(sku);
            if (product == null) {
                throw new IllegalArgumentException("Product not found: " + sku);
            }
            
            if (product.getQuantityAvailable() < newQty) {
                throw new IllegalArgumentException("Insufficient stock for product: " + sku);
            }
            
            line.setQty(newQty);
        } else {
            // Remove item if quantity is 0
            cart.getLines().remove(line);
        }
        
        // Recalculate totals
        recalculateTotals(cart);
        
        // Update timestamps
        cart.setUpdatedAt(Instant.now());
        
        logger.info("Item updated in cart successfully: {}", cart.getCartId());
        return cart;
    }

    private String extractSkuFromContext(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        // TODO: Extract from context - placeholder implementation
        return null;
    }

    private Integer extractQtyFromContext(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        // TODO: Extract from context - placeholder implementation
        return null;
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
