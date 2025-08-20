package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
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

@Component
public class CreateCartProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateCartProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateCartProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart creation for request: {}", request.getId());

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
        return cart != null && cart.isValid();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        logger.debug("CreateCartProcessor - before: {}", cart);
        // Initialize defaults
        if (cart.getStatus() == null || cart.getStatus().isBlank()) {
            cart.setStatus("NEW");
        }
        // On create, immediately transition to ACTIVE as per workflow
        if ("NEW".equalsIgnoreCase(cart.getStatus())) {
            cart.setStatus("ACTIVE");
        }
        if (cart.getTotalItems() == null) cart.setTotalItems(0);
        if (cart.getGrandTotal() == null) cart.setGrandTotal(0.0);
        cart.setUpdatedAt(OffsetDateTime.now());
        logger.info("Cart {} transitioned to status {}", cart.getCartId(), cart.getStatus());
        return cart;
    }
}
