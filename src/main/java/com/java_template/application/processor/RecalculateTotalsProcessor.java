package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.CartLine;
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

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class RecalculateTotalsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RecalculateTotalsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RecalculateTotalsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Recalculating totals for Cart request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        return cart != null && cart.getLines() != null;
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        List<CartLine> lines = cart.getLines();
        int totalItems = 0;
        double grandTotal = 0.0;

        if (lines != null) {
            for (CartLine line : lines) {
                if (line.getQty() == null || line.getPrice() == null) continue; // defensive
                if (line.getQty() < 1) line.setQty(1);
                double lineTotal = line.getPrice() * line.getQty();
                // set lineTotal if property exists on CartLine - CartLine doesn't have lineTotal so we can't set it
                totalItems += line.getQty();
                grandTotal += lineTotal;
            }
        }

        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);
        cart.setUpdatedAt(OffsetDateTime.now());
        logger.info("Recalculated Cart {} totals: items={}, grandTotal={}", cart.getCartId(), totalItems, grandTotal);
        return cart;
    }
}
