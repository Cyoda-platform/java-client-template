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

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Cart has no items")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        return cart != null && cart.getItems() != null && !cart.getItems().isEmpty();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart entity = context.entity();

        if (entity == null) {
            logger.warn("BeginCheckoutProcessor received null entity in context");
            return null;
        }

        // Ensure cart has items (redundant due to validate, but safe for idempotency)
        if (entity.getItems() == null || entity.getItems().isEmpty()) {
            logger.error("Cart {} has no items - cannot begin checkout", entity.getId());
            throw new RuntimeException("Cart has no items");
        }

        String currentStatus = entity.getStatus();
        if (currentStatus == null || currentStatus.isBlank()) {
            currentStatus = "NEW";
        }

        // If cart already converted, do not allow checkout
        if ("CONVERTED".equalsIgnoreCase(currentStatus)) {
            logger.warn("Cart {} is already CONVERTED, cannot begin checkout", entity.getId());
            return entity;
        }

        // If already checking out, nothing to do (idempotent)
        if ("CHECKING_OUT".equalsIgnoreCase(currentStatus)) {
            logger.debug("Cart {} already in CHECKING_OUT state", entity.getId());
            return entity;
        }

        // Transition to CHECKING_OUT
        entity.setStatus("CHECKING_OUT");
        entity.setUpdatedAt(Instant.now().toString());
        logger.info("Cart {} transitioned to CHECKING_OUT", entity.getId());

        return entity;
    }
}