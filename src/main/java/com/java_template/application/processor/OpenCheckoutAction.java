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

@Component
public class OpenCheckoutAction implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OpenCheckoutAction.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OpenCheckoutAction(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart entity) {
        if (entity == null) return false;
        // ensure entity passes its internal validation
        if (!entity.isValid()) return false;
        // Only allow transition when current status is ACTIVE
        String status = entity.getStatus();
        if (status == null) return false;
        return "ACTIVE".equalsIgnoreCase(status);
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart entity = context.entity();

        // Business rule: OPEN_CHECKOUT transitions ACTIVE -> CHECKING_OUT
        // Only modify the triggering entity's state; persistence is handled by Cyoda workflow
        entity.setStatus("CHECKING_OUT");
        // update timestamp
        entity.setUpdatedAt(Instant.now().toString());

        logger.info("Cart {} moved to CHECKING_OUT", entity.getCartId());

        return entity;
    }
}