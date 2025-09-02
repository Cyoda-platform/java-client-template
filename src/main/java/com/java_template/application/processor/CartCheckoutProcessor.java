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
import java.util.List;
import java.util.Optional;

@Component
public class CartCheckoutProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartCheckoutProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CartCheckoutProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart checkout for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::processCartCheckout)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null && cart.isValid();
    }

    private Cart processCartCheckout(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Validate cart has at least one line item
        if (cart.getLines() == null || cart.getLines().isEmpty()) {
            throw new IllegalArgumentException("Cart must have at least one line item");
        }

        // Validate all products in cart are still ACTIVE and available
        double recalculatedTotal = 0.0;
        int recalculatedItems = 0;

        for (Cart.CartLine line : cart.getLines()) {
            // Get current product details
            Condition skuCondition = Condition.of("$.sku", "EQUALS", line.getSku());
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(skuCondition));

            Optional<EntityResponse<Product>> productResponse = entityService.getFirstItemByCondition(
                Product.class, Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true);

            if (productResponse.isEmpty()) {
                throw new IllegalArgumentException("Product no longer available: " + line.getSku());
            }

            Product product = productResponse.get().getData();
            String productState = productResponse.get().getMetadata().getState();

            if (!"ACTIVE".equals(productState)) {
                throw new IllegalArgumentException("Product no longer active: " + line.getSku());
            }

            if (product.getQuantityAvailable() < line.getQty()) {
                throw new IllegalArgumentException("Insufficient stock for product: " + line.getSku());
            }

            // Update line with current product price
            line.setPrice(product.getPrice());
            line.setName(product.getName());

            recalculatedTotal += line.getPrice() * line.getQty();
            recalculatedItems += line.getQty();
        }

        // Recalculate totals with current product prices
        cart.setTotalItems(recalculatedItems);
        cart.setGrandTotal(recalculatedTotal);

        // Set updatedAt timestamp
        cart.setUpdatedAt(Instant.now());

        logger.info("Cart {} ready for checkout with total: {}", cart.getCartId(), cart.getGrandTotal());
        return cart;
    }
}
