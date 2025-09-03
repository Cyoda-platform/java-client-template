package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CartRecalculateTotalsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartRecalculateTotalsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CartRecalculateTotalsProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart recalculate totals for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract cart entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract cart entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::processCartLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null && cart.isValid();
    }

    private Cart processCartLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        EntityProcessorCalculationRequest request = context.request();

        try {
            // Check if there's line item data in the request payload
            Map<String, Object> payloadMap = objectMapper.convertValue(request.getPayload().getData(), Map.class);
            
            if (payloadMap.containsKey("lineItemData")) {
                Map<String, Object> lineItemData = (Map<String, Object>) payloadMap.get("lineItemData");
                String sku = (String) lineItemData.get("sku");
                Integer qty = (Integer) lineItemData.get("qty");
                
                if (sku != null && qty != null) {
                    updateCartLine(cart, sku, qty);
                }
            }

            // Recalculate totals
            recalculateTotals(cart);

            logger.info("Cart totals recalculated - Items: {}, Total: {}", cart.getTotalItems(), cart.getGrandTotal());
            return cart;

        } catch (Exception e) {
            logger.error("Error processing cart logic: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process cart recalculation: " + e.getMessage(), e);
        }
    }

    private void updateCartLine(Cart cart, String sku, Integer qty) {
        if (cart.getLines() == null) {
            cart.setLines(new ArrayList<>());
        }

        // Find existing line
        Cart.CartLine existingLine = cart.getLines().stream()
            .filter(line -> sku.equals(line.getSku()))
            .findFirst()
            .orElse(null);

        if (existingLine != null) {
            if (qty > 0) {
                existingLine.setQty(qty);
            } else {
                cart.getLines().remove(existingLine);
            }
        } else if (qty > 0) {
            // Add new line - need to get product details
            try {
                var productResponse = entityService.findByField(
                    Product.class, 
                    Product.ENTITY_NAME, 
                    Product.ENTITY_VERSION, 
                    "sku", 
                    sku
                );
                
                if (!productResponse.isEmpty()) {
                    Product product = productResponse.get(0).getData();
                    Cart.CartLine newLine = new Cart.CartLine();
                    newLine.setSku(sku);
                    newLine.setName(product.getName());
                    newLine.setPrice(product.getPrice());
                    newLine.setQty(qty);
                    cart.getLines().add(newLine);
                } else {
                    logger.warn("Product not found for SKU: {}", sku);
                    throw new RuntimeException("Product not found for SKU: " + sku);
                }
            } catch (Exception e) {
                logger.error("Error fetching product for SKU {}: {}", sku, e.getMessage(), e);
                throw new RuntimeException("Failed to fetch product details: " + e.getMessage(), e);
            }
        }
    }

    private void recalculateTotals(Cart cart) {
        if (cart.getLines() == null) {
            cart.setLines(new ArrayList<>());
        }

        int totalItems = 0;
        double grandTotal = 0.0;

        for (Cart.CartLine line : cart.getLines()) {
            if (line.getQty() != null && line.getPrice() != null) {
                totalItems += line.getQty();
                grandTotal += (line.getPrice() * line.getQty());
            }
        }

        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);
    }
}
