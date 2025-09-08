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
 * CartCreateProcessor - Initializes a new cart
 * 
 * This processor handles:
 * - Setting initial cart status to NEW
 * - Initializing empty lines list
 * - Setting initial totals to zero
 * - Setting creation timestamp
 * 
 * Triggered by: CREATE_ON_FIRST_ADD transition
 */
@Component
public class CartCreateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartCreateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CartCreateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Cart.class)
            .validate(this::isValidEntityWithMetadata, "Invalid cart entity")
            .map(this::processCartCreationLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the Cart EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Cart> entityWithMetadata) {
        Cart cart = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return cart != null && technicalId != null;
    }

    /**
     * Main business logic for cart creation
     */
    private EntityWithMetadata<Cart> processCartCreationLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {
        
        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Initializing new cart: {}", cart.getCartId());

        // Initialize cart with default values
        if (cart.getLines() == null) {
            cart.setLines(new ArrayList<>());
        }
        
        if (cart.getTotalItems() == null) {
            cart.setTotalItems(0);
        }
        
        if (cart.getGrandTotal() == null) {
            cart.setGrandTotal(0.0);
        }

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
