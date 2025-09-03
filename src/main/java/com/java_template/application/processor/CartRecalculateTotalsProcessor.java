package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

@Component
public class CartRecalculateTotalsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartRecalculateTotalsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CartRecalculateTotalsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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

        try {
            // The cart entity itself contains all the line item data - no need for payload extraction
            // Simply recalculate totals based on current cart lines
            recalculateTotals(cart);

            logger.info("Cart totals recalculated - Items: {}, Total: {}", cart.getTotalItems(), cart.getGrandTotal());
            return cart;

        } catch (Exception e) {
            logger.error("Error processing cart logic: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process cart recalculation: " + e.getMessage(), e);
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
