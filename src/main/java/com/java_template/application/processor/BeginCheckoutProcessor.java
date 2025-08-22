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

import java.time.Instant;

@Component
public class BeginCheckoutProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BeginCheckoutProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public BeginCheckoutProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Cart.class)
            .validate(this::hasItems, "Cart has no items")
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart entity) {
        return entity != null && entity.isValid();
    }

    private boolean hasItems(Cart cart) {
        return cart != null && cart.getItems() != null && !cart.getItems().isEmpty();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart entity = context.entity();

        // Idempotent transition to CHECKING_OUT
        if (entity == null) return entity;

        String currentStatus = entity.getStatus();
        if (!"CHECKING_OUT".equals(currentStatus)) {
            // Only move to CHECKING_OUT if not already in terminal CONVERTED
            if ("CONVERTED".equals(currentStatus)) {
                logger.warn("Cart {} is already CONVERTED, cannot begin checkout", entity.getId());
                return entity;
            }
            entity.setStatus("CHECKING_OUT");
            entity.setUpdatedAt(Instant.now().toString());
            logger.info("Cart {} transitioned to CHECKING_OUT", entity.getId());
        } else {
            // already in CHECKING_OUT - keep idempotent
            logger.debug("Cart {} already in CHECKING_OUT state", entity.getId());
        }

        return entity;
    }
}