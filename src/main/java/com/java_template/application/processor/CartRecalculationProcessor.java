package com.java_template.application.processor;

import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
import com.java_template.application.entity.shoppingcart.version_1.ShoppingCartItem;
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
import java.util.Objects;

@Component
public class CartRecalculationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartRecalculationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CartRecalculationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ShoppingCart recalculation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ShoppingCart.class)
            .validate(this::isValidEntity, "Invalid shopping cart for recalculation")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ShoppingCart cart) {
        return cart != null && cart.getItems() != null;
    }

    private ShoppingCart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ShoppingCart> context) {
        ShoppingCart cart = context.entity();
        try {
            BigDecimal subtotal = BigDecimal.ZERO;
            for (ShoppingCartItem item : cart.getItems()) {
                if (item == null) continue;
                Integer qty = item.getQuantity() == null ? 0 : item.getQuantity();
                BigDecimal unit = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
                BigDecimal line = unit.multiply(BigDecimal.valueOf(qty));
                subtotal = subtotal.add(line);
            }
            cart.setSubtotal(subtotal);
            // Simple tax and shipping estimation: tax = 10% of subtotal, shipping = fixed 5.00 if subtotal > 0
            BigDecimal taxes = subtotal.multiply(new BigDecimal("0.10"));
            BigDecimal shipping = subtotal.compareTo(BigDecimal.ZERO) > 0 ? new BigDecimal("5.00") : BigDecimal.ZERO;
            cart.setTaxes(taxes);
            cart.setShipping(shipping);
            cart.setTotal(subtotal.add(taxes).add(shipping));
            cart.setStatus("Open");
            logger.info("Recalculated cart {} totals: subtotal={}, taxes={}, shipping={}, total={}", cart.getId(), subtotal, taxes, shipping, cart.getTotal());
        } catch (Exception e) {
            logger.error("Error during cart recalculation: {}", e.getMessage(), e);
        }
        return cart;
    }
}
