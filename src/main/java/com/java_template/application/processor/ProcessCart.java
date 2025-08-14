package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Component
public class ProcessCart implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessCart.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProcessCart(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid Cart entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        // Basic validation: Cart must not be null, status must be valid, items not null
        if (cart == null) return false;
        if (cart.getStatus() == null || cart.getItems() == null) return false;
        return true;
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        logger.info("Start processing Cart with ID: {}", cart.getCartId());

        // Business logic:
        // - Validate product availability for each CartItem
        // - Update Cart status to ACTIVE if validation passes
        // - This processor just prepares for validation criteria to run

        // TODO: Implement detailed stock validation logic if needed here, or rely on criteria
        // For now, we assume stock validation is done elsewhere
        
        // Set Cart status to 'processing' during processing (if mutable allowed)
        // But as per functional requirements, status update to ACTIVE is done after validation

        logger.info("Completed processing Cart with ID: {}", cart.getCartId());
        return cart;
    }
}
