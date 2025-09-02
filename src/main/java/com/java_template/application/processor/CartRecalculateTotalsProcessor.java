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

import java.time.Instant;

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
        logger.info("Processing Cart totals recalculation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart entity) {
        return entity != null && entity.isValid();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        try {
            // Recalculate totals
            int totalItems = 0;
            double grandTotal = 0.0;

            if (cart.getLines() != null) {
                for (Cart.CartLine line : cart.getLines()) {
                    if (line.getQty() != null && line.getPrice() != null) {
                        totalItems += line.getQty();
                        grandTotal += (line.getPrice() * line.getQty());
                    }
                }
            }

            cart.setTotalItems(totalItems);
            cart.setGrandTotal(grandTotal);
            cart.setUpdatedAt(Instant.now());

            logger.info("Recalculated cart {} totals: {} items, ${}",
                cart.getCartId(), totalItems, grandTotal);

            return cart;

        } catch (Exception e) {
            logger.error("Error recalculating cart totals: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to recalculate cart totals: " + e.getMessage(), e);
        }
    }
}