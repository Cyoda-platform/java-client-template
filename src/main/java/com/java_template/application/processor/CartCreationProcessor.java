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
import java.util.UUID;

@Component
public class CartCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CartCreationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::processCartCreation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null;
    }

    private Cart processCartCreation(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Generate unique cartId if not set
        if (cart.getCartId() == null || cart.getCartId().trim().isEmpty()) {
            cart.setCartId("cart-" + UUID.randomUUID().toString());
        }

        // Validate first item has sku and qty > 0
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            throw new IllegalArgumentException("Cart must have at least one item");
        }

        Cart.CartLine firstLine = cart.getLines().get(0);
        if (firstLine.getSku() == null || firstLine.getSku().trim().isEmpty()) {
            throw new IllegalArgumentException("First item must have a valid SKU");
        }
        if (firstLine.getQty() == null || firstLine.getQty() <= 0) {
            throw new IllegalArgumentException("First item quantity must be greater than 0");
        }

        // Get product details from Product entity by sku
        Condition skuCondition = Condition.of("$.sku", "EQUALS", firstLine.getSku());
        SearchConditionRequest condition = new SearchConditionRequest();
        condition.setType("group");
        condition.setOperator("AND");
        condition.setConditions(List.of(skuCondition));

        Optional<EntityResponse<Product>> productResponse = entityService.getFirstItemByCondition(
            Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);

        if (productResponse.isEmpty()) {
            throw new IllegalArgumentException("Product not found: " + firstLine.getSku());
        }

        Product product = productResponse.get().getData();
        String productState = productResponse.get().getMetadata().getState();

        if (!"ACTIVE".equals(productState)) {
            throw new IllegalArgumentException("Product not available: " + firstLine.getSku());
        }

        // Create cart line with product details
        firstLine.setName(product.getName());
        firstLine.setPrice(product.getPrice());

        // Initialize lines list if needed
        if (cart.getLines() == null) {
            cart.setLines(new ArrayList<>());
        }

        // Calculate totalItems and grandTotal
        cart.setTotalItems(firstLine.getQty());
        cart.setGrandTotal(firstLine.getPrice() * firstLine.getQty());

        // Set timestamps
        Instant now = Instant.now();
        cart.setCreatedAt(now);
        cart.setUpdatedAt(now);

        logger.info("Cart {} created successfully with first item {}", cart.getCartId(), firstLine.getSku());
        return cart;
    }
}
