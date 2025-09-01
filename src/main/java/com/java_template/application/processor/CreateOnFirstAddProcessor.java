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

import java.time.Instant;
import java.util.UUID;

@Component
public class CreateOnFirstAddProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOnFirstAddProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateOnFirstAddProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract cart entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract cart entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::processCartCreation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null && cart.getLines() != null && !cart.getLines().isEmpty();
    }

    private Cart processCartCreation(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Set cart ID if not present
        if (cart.getCartId() == null || cart.getCartId().isBlank()) {
            cart.setCartId("CART-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        // Set status to ACTIVE (first add creates active cart)
        cart.setStatus("ACTIVE");

        // Set timestamps
        String now = Instant.now().toString();
        cart.setCreatedAt(now);
        cart.setUpdatedAt(now);

        // Calculate totals
        int totalItems = 0;
        double grandTotal = 0.0;

        for (Cart.Line line : cart.getLines()) {
            if (line.getQty() != null && line.getPrice() != null) {
                totalItems += line.getQty();
                grandTotal += line.getQty() * line.getPrice();
            }
        }

        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);

        logger.info("Created cart {} with {} items, total: {}", cart.getCartId(), totalItems, grandTotal);

        return cart;
    }
}