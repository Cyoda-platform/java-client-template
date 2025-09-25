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
 * Processor to initialize cart on first add
 * Transitions cart from initial to active state
 */
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
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Cart.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cart entity wrapper")
                .map(this::processCartInitialization)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Cart> entityWithMetadata) {
        Cart cart = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return cart != null && cart.getCartId() != null && technicalId != null;
    }

    /**
     * Initializes cart for first use
     */
    private EntityWithMetadata<Cart> processCartInitialization(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Cart> context) {

        EntityWithMetadata<Cart> entityWithMetadata = context.entityResponse();
        Cart cart = entityWithMetadata.entity();

        logger.debug("Initializing cart: {}", cart.getCartId());

        // Initialize cart properties
        cart.setStatus("ACTIVE");
        
        // Initialize empty lines if not present
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

        logger.info("Cart {} initialized and activated", cart.getCartId());

        return entityWithMetadata;
    }
}
