package com.java_template.application.processor;

import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
import com.java_template.application.entity.shoppingcart.version_1.CartItem;
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

@Component
public class CartUpdateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartUpdateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CartUpdateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing cart update for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ShoppingCart.class)
            .validate(this::isValidEntity, "Invalid shopping cart for update")
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
            BigDecimal subtotal = BigDecimal.ZERO;
            if (cart.getItems() != null) {
                for (CartItem item : cart.getItems()) {
                    if (item.getQuantity() == null || item.getQuantity() <= 0) {
                        logger.warn("Cart {} has item {} with invalid quantity", cart.getId(), item.getProductId());
                        continue;
                    }
                    BigDecimal price = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
                    subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(item.getQuantity())));
                }
            }
            cart.setSubtotal(subtotal);
            // For prototype, taxes/shipping/discounts not calculated
            cart.setTotal(cart.getSubtotal());
            logger.info("Cart {} updated. subtotal={} total={}", cart.getId(), cart.getSubtotal(), cart.getTotal());
        } catch (Exception e) {
            logger.error("Error updating cart {}: {}", cart != null ? cart.getId() : "<null>", e.getMessage());
        }
        return cart;
    }
}
