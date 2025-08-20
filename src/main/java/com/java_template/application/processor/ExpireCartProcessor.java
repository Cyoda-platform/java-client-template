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

import java.time.Instant;

@Component
public class ExpireCartProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExpireCartProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ExpireCartProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ExpireCart for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart state for expiration")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        return cart != null && cart.getExpiresAt() != null;
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        try {
            Instant now = Instant.now();
            Instant expires = Instant.parse(cart.getExpiresAt());
            if (now.isAfter(expires)) {
                if ("RESERVED".equals(cart.getStatus())) {
                    // Set to EXPIRED and rely on ReleaseReservationProcessor to handle stock release
                    cart.setStatus("EXPIRED");
                } else {
                    cart.setStatus("EXPIRED");
                }
                cart.setUpdatedAt(Instant.now().toString());
                logger.info("Cart {} expired", cart.getCartId());
            }
        } catch (Exception e) {
            logger.warn("Failed to evaluate cart expiry", e);
        }
        return cart;
    }
}
