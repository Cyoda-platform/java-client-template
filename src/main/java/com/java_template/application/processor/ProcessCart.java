package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cartitem.version_1.CartItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class ProcessCart implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessCart.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ProcessCart(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid Cart entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        if (cart == null) return false;
        if (cart.getStatus() == null || cart.getItems() == null) return false;
        return true;
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        logger.info("Start processing Cart with ID: {}", cart.getCartId());

        // Business logic:
        // 1. Validate product availability for each CartItem
        // 2. Update Cart status to ACTIVE if validation passes

        boolean allProductsAvailable = true;
        List<CartItem> items = cart.getItems();

        // Check stock availability for each cart item
        for (CartItem item : items) {
            if (!isProductAvailable(item.getProductId(), item.getQuantity())) {
                allProductsAvailable = false;
                logger.warn("Product {} not available in sufficient quantity", item.getProductId());
                break;
            }
        }

        if (allProductsAvailable) {
            cart.setStatus("ACTIVE");
            logger.info("Cart {} status set to ACTIVE", cart.getCartId());
        } else {
            cart.setStatus("FAILED");
            logger.warn("Cart {} status set to FAILED due to stock validation failure", cart.getCartId());
        }

        logger.info("Completed processing Cart with ID: {}", cart.getCartId());
        return cart;
    }

    private boolean isProductAvailable(String productId, Integer quantity) {
        // Implement actual stock check logic with inventory service
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.productId", "EQUALS", productId),
                    Condition.of("$.quantity", "GREATER_THAN", 0)
            );
            CompletableFuture<java.util.List<?>> future = entityService.getItemsByCondition(
                    "Product", "1",
                    condition,
                    true
            );
            java.util.List<?> products = future.get();

            // For simplification, assume product record exists means availability
            return products != null && !products.isEmpty();

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error checking product availability for productId {}: {}", productId, e.getMessage());
            return false;
        }
    }
}
