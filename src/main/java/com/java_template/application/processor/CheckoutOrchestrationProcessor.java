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

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class CheckoutOrchestrationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutOrchestrationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CheckoutOrchestrationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart checkout orchestration for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract cart entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract cart entity: " + error.getMessage());
            })
            .validate(this::isValidCartForCheckout, "Invalid cart state for checkout")
            .map(this::processCheckoutOrchestration)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCartForCheckout(Cart cart) {
        return cart != null &&
               cart.getCartId() != null &&
               cart.getLines() != null &&
               !cart.getLines().isEmpty() &&
               cart.getGuestContact() != null &&
               cart.getGuestContact().getName() != null;
    }

    private Cart processCheckoutOrchestration(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Set status to CONVERTED (checkout completed)
        cart.setStatus("CONVERTED");
        cart.setUpdatedAt(Instant.now().toString());

        logger.info("Cart {} checkout orchestration completed - status set to CONVERTED", cart.getCartId());

        // This processor signals that the cart is ready for order creation
        // The actual order creation will be triggered by the payment completion

        return cart;
    }
}