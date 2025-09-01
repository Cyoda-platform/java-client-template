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

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class CartRecalculateTotalsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartRecalculateTotalsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

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
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid cart state")
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
        
        logger.info("Recalculating totals for cart: {}", cart.getCartId());

        // Validate cart entity exists
        if (cart == null) {
            logger.error("Cart entity is null");
            throw new IllegalArgumentException("Cart entity cannot be null");
        }

        // Initialize lines if null
        if (cart.getLines() == null) {
            cart.setLines(new java.util.ArrayList<>());
        }

        // Calculate totals
        int totalItems = 0;
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (Cart.CartLine line : cart.getLines()) {
            if (line != null && line.getQty() != null && line.getPrice() != null) {
                // Validate line data
                if (line.getQty() <= 0) {
                    logger.warn("Invalid line quantity for SKU {}: {}", line.getSku(), line.getQty());
                    continue;
                }
                
                if (line.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                    logger.warn("Invalid line price for SKU {}: {}", line.getSku(), line.getPrice());
                    continue;
                }

                // Calculate line total
                BigDecimal lineTotal = line.getPrice().multiply(BigDecimal.valueOf(line.getQty()));
                
                // Add to totals
                totalItems += line.getQty();
                grandTotal = grandTotal.add(lineTotal);
                
                logger.debug("Line {}: qty={}, price={}, lineTotal={}", 
                           line.getSku(), line.getQty(), line.getPrice(), lineTotal);
            }
        }

        // Update cart totals
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);
        cart.setUpdatedAt(LocalDateTime.now());

        logger.info("Cart totals recalculated - totalItems: {}, grandTotal: {}", totalItems, grandTotal);

        return cart;
    }
}
