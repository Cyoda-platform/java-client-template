package com.java_template.application.processor;

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

@Component
public class CartCheckoutProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartCheckoutProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CartCheckoutProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ShoppingCart checkout for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ShoppingCart.class)
            .validate(this::isValidEntity, "Invalid shopping cart state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ShoppingCart cart) {
        if (cart == null) return false;
        return cart.getItems() != null && !cart.getItems().isEmpty();
    }

    private ShoppingCart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ShoppingCart> context) {
        ShoppingCart cart = context.entity();
        // Mark the cart as checked out and ensure totals are present
        try {
            cart.setStatus("CheckedOut");
            if (cart.getSubtotal() == null) {
                cart.setSubtotal(cart.getSubtotal() == null ? java.math.BigDecimal.ZERO : cart.getSubtotal());
            }
            if (cart.getTotal() == null) {
                cart.setTotal(cart.getSubtotal() == null ? java.math.BigDecimal.ZERO : cart.getSubtotal());
            }
            logger.info("Cart {} checked out", cart.getId());
        } catch (Exception e) {
            logger.error("Error during cart checkout processing: {}", e.getMessage(), e);
        }
        return cart;
    }
}
