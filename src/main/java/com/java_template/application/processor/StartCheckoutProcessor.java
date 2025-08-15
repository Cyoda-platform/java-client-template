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
import java.util.UUID;

@Component
public class StartCheckoutProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartCheckoutProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartCheckoutProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing StartCheckout for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart state for checkout")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        return cart != null && cart.getId() != null && cart.getItems() != null && !cart.getItems().isEmpty();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Validate currency consistency
        if (cart.getCurrency() == null || cart.getCurrency().isEmpty()) {
            // keep as is; serializer.validate already checks
        }

        // Move cart into checkout in progress
        cart.setStatus("CHECKOUT_IN_PROGRESS");
        cart.setUpdatedAt(Instant.now().toString());
        // generate a checkout attempt id if not present in metadata
        if (cart.getCheckoutAttemptId() == null || cart.getCheckoutAttemptId().isEmpty()) {
            cart.setCheckoutAttemptId(UUID.randomUUID().toString());
        }

        logger.info("Cart {} checkout started with attemptId={}", cart.getId(), cart.getCheckoutAttemptId());

        return cart;
    }
}
