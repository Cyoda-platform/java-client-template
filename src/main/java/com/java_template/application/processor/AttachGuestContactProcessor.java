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
public class AttachGuestContactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AttachGuestContactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AttachGuestContactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart guest contact attachment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract cart entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract cart entity: " + error.getMessage());
            })
            .validate(this::isValidCart, "Invalid cart state")
            .map(this::attachGuestContact)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidCart(Cart cart) {
        return cart != null && cart.getCartId() != null;
    }

    private Cart attachGuestContact(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        // Update timestamp
        cart.setUpdatedAt(Instant.now().toString());

        // Validate guest contact if present
        if (cart.getGuestContact() != null) {
            Cart.GuestContact contact = cart.getGuestContact();

            // Ensure required fields are present
            if (contact.getName() == null || contact.getName().isBlank()) {
                logger.warn("Guest contact name is missing for cart {}", cart.getCartId());
            }

            if (contact.getAddress() != null) {
                Cart.Address address = contact.getAddress();
                if (address.getLine1() == null || address.getLine1().isBlank() ||
                    address.getCountry() == null || address.getCountry().isBlank()) {
                    logger.warn("Guest contact address is incomplete for cart {}", cart.getCartId());
                }
            }

            logger.info("Attached guest contact to cart {}: {}", cart.getCartId(), contact.getName());
        }

        return cart;
    }
}