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
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::processCartAddItem)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null && cart.isValid();
    }

    private Cart processCartAddItem(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Note: In a real implementation, the item to add would come from the request payload
        // For this demo, we'll assume it's passed in some way or we extract it from the request
        // This is a simplified implementation - in practice, you'd extract the item details from the request

        // For now, let's assume we're adding the last item in the lines array as the new item
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            throw new IllegalArgumentException("No item to add found in request");
        }

        Cart.CartLine itemToAdd = cart.getLines().get(cart.getLines().size() - 1);

        // Validate item has sku and qty > 0
        if (itemToAdd.getSku() == null || itemToAdd.getSku().trim().isEmpty()) {
            throw new IllegalArgumentException("Item must have a valid SKU");
        }
        if (itemToAdd.getQty() == null || itemToAdd.getQty() <= 0) {
            throw new IllegalArgumentException("Item quantity must be greater than 0");
        }

        // Get product details from Product entity by sku
        Condition skuCondition = Condition.of("$.sku", "EQUALS", itemToAdd.getSku());
        SearchConditionRequest condition = new SearchConditionRequest();
        condition.setType("group");
        condition.setOperator("AND");
        condition.setConditions(List.of(skuCondition));

        Optional<EntityResponse<Product>> productResponse = entityService.getFirstItemByCondition(
            Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);

        if (productResponse.isEmpty()) {
            throw new IllegalArgumentException("Product not found: " + itemToAdd.getSku());
        }

        Product product = productResponse.get().getData();
        String productState = productResponse.get().getMetadata().getState();

        if (!"ACTIVE".equals(productState)) {
            throw new IllegalArgumentException("Product not available: " + itemToAdd.getSku());
        }

        // Initialize lines if needed
        if (cart.getLines() == null) {
            cart.setLines(new ArrayList<>());
        }

        // Find existing line with same sku
        Cart.CartLine existingLine = cart.getLines().stream()
            .filter(line -> itemToAdd.getSku().equals(line.getSku()))
            .findFirst()
            .orElse(null);

        if (existingLine != null) {
            // Increment quantity by itemToAdd.qty
            existingLine.setQty(existingLine.getQty() + itemToAdd.getQty());
        } else {
            // Add new line with product details
            itemToAdd.setName(product.getName());
            itemToAdd.setPrice(product.getPrice());
            // Note: itemToAdd is already in the lines list, so we don't need to add it again
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

        logger.info("Item {} added to cart {}", itemToAdd.getSku(), cart.getCartId());
        return cart;
    }
}
