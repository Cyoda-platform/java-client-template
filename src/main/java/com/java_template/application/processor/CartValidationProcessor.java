package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CartValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CartValidationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CartValidation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ShoppingCart.class)
            .validate(this::isValidEntity, "Invalid cart state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ShoppingCart cart) {
        return cart != null && cart.isValid();
    }

    private ProcessorSerializer.ProcessorEntityExecutionContext<ShoppingCart> processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ShoppingCart> context) {
        ShoppingCart cart = context.entity();
        try {
            // Basic validation and timestamp management
            String now = Instant.now().toString();
            if (cart.getCreatedAt() == null || cart.getCreatedAt().isBlank()) cart.setCreatedAt(now);
            cart.setUpdatedAt(now);

            // Validate items
            boolean hasItems = cart.getItems() != null && !cart.getItems().isEmpty();
            boolean invalidItem = false;
            if (hasItems) {
                for (ShoppingCart.CartItem it : cart.getItems()) {
                    if (it == null || it.getProductId() == null || it.getProductId().isBlank() || it.getQuantity() == null || it.getQuantity() <= 0) {
                        invalidItem = true; break;
                    }
                }
            }

            if (!hasItems || invalidItem) {
                cart.setStatus("CHECKOUT_FAILED");
            } else {
                if (cart.getStatus() == null || cart.getStatus().isBlank()) cart.setStatus("ACTIVE");
            }

            // Persist updated cart state if technicalId present
            if (context.attributes() != null && context.attributes().get("technicalId") != null) {
                String tid = (String) context.attributes().get("technicalId");
                ObjectNode node = com.java_template.common.util.Json.mapper().convertValue(cart, ObjectNode.class);
                CompletableFuture<UUID> fut = entityService.updateItem(ShoppingCart.ENTITY_NAME, String.valueOf(ShoppingCart.ENTITY_VERSION), UUID.fromString(tid), node);
                fut.whenComplete((id, ex) -> {
                    if (ex != null) logger.error("Failed to persist cart after validation", ex);
                });
            }

        } catch (Exception ex) {
            logger.error("Error in CartValidationProcessor", ex);
        }
        return context;
    }
}
