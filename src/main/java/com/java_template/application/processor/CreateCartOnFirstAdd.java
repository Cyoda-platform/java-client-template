package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Processor to initialize a new cart when first item is added.
 * Sets up initial cart state and prepares for line items.
 */
@Component
public class CreateCartOnFirstAdd implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateCartOnFirstAdd.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateCartOnFirstAdd(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Cart.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cart entity wrapper")
                .map(this::initializeCart)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for Cart
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Cart> entityWithMetadata) {
        Cart cart = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return cart != null && technicalId != null;
    }

    /**
     * Initializes a new cart with default values
     */
    private EntityWithMetadata<Cart> initializeCart(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Initializing new cart: {}", cart.getCartId());

        // Initialize cart state
        cart.setStatus("ACTIVE");
        
        // Initialize empty lines if not set
        if (cart.getLines() == null) {
            cart.setLines(new ArrayList<>());
        }
        
        // Initialize totals
        cart.setTotalItems(0);
        cart.setGrandTotal(0.0);
        
        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        if (cart.getCreatedAt() == null) {
            cart.setCreatedAt(now);
        }
        cart.setUpdatedAt(now);

        logger.info("Cart {} initialized successfully", cart.getCartId());

        return entityWithMetadata;
    }
}
