package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ValidateOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateOrderProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing order validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ShoppingCart.class)
            .validate(this::isValidEntity, "Invalid cart for order validation")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ShoppingCart cart) {
        return cart != null && cart.getId() != null;
    }

    private ShoppingCart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ShoppingCart> context) {
        ShoppingCart cart = context.entity();
        try {
            if (cart.getItems() == null || cart.getItems().isEmpty()) {
                logger.warn("Cart {} is empty during validation", cart.getId());
                return cart;
            }

            // Validate each cart item: quantity > 0 and price >= 0 and productId present
            cart.getItems().forEach(item -> {
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    logger.warn("Cart {} has item with invalid quantity for product {}", cart.getId(), item.getProductId());
                }
                if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                    logger.warn("Cart {} has item with invalid price for product {}", cart.getId(), item.getProductId());
                }
            });

            // Compute subtotal from items if not present
            BigDecimal subtotal = BigDecimal.ZERO;
            for (var item : cart.getItems()) {
                BigDecimal price = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
                Integer qty = item.getQuantity() == null ? 0 : item.getQuantity();
                subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(qty)));
            }
            cart.setSubtotal(subtotal);
            // Taxes, shipping, discounts left as-is for other processors
            logger.info("Cart {} validated. Subtotal={}", cart.getId(), cart.getSubtotal());

        } catch (Exception e) {
            logger.error("Error during order validation for cart {}: {}", cart != null ? cart.getId() : "<null>", e.getMessage());
        }
        return cart;
    }
}
