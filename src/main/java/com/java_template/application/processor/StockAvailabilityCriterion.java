package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shoppingcart.version_1.ShoppingCart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class StockAvailabilityCriterion implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StockAvailabilityCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public StockAvailabilityCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing StockAvailabilityCriterion for request: {}", request.getId());

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
            if (cart.getItems() == null || cart.getItems().isEmpty()) return context;

            for (ShoppingCart.CartItem it : cart.getItems()) {
                String prodId = it.getProductId();
                Integer qty = it.getQuantity();
                if (prodId == null || prodId.isBlank() || qty == null || qty <= 0) {
                    logger.warn("Invalid cart item detected: {}", it);
                    continue;
                }

                CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> fut = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    SearchConditionRequest.group("AND", Condition.of("$.id", "EQUALS", prodId)),
                    true
                );
                com.fasterxml.jackson.databind.node.ArrayNode found = fut.get();
                if (found == null || found.size() == 0) {
                    // Product does not exist -> fail checkout
                    failCart(context, cart, "product not found: " + prodId);
                    return context;
                }
                ObjectNode pnode = (ObjectNode) found.get(0);
                int stock = pnode.has("stockQuantity") ? pnode.get("stockQuantity").asInt() : 0;
                int reserved = pnode.has("reservedQuantity") ? pnode.get("reservedQuantity").asInt() : 0;
                int effective = stock - reserved;
                if (effective < qty) {
                    failCart(context, cart, "insufficient stock for product " + prodId + ": required=" + qty + " available=" + effective);
                    return context;
                }
            }

            // All items available
            logger.info("Stock availability check passed for cart belonging to {}", cart.getCustomerId());
        } catch (Exception ex) {
            logger.error("Error during stock availability check", ex);
            // best-effort mark checkout failed
            failCart(context, cart, "error checking stock availability");
        }
        return context;
    }

    private void failCart(ProcessorSerializer.ProcessorEntityExecutionContext<ShoppingCart> context, ShoppingCart cart, String reason) {
        try {
            logger.warn("Failing cart checkout due to: {}", reason);
            cart.setStatus("CHECKOUT_FAILED");
            if (context.attributes() != null && context.attributes().get("technicalId") != null) {
                String tid = (String) context.attributes().get("technicalId");
                ObjectNode node = com.java_template.common.util.Json.mapper().convertValue(cart, ObjectNode.class);
                entityService.updateItem(ShoppingCart.ENTITY_NAME, String.valueOf(ShoppingCart.ENTITY_VERSION), UUID.fromString(tid), node).whenComplete((id, ex) -> {
                    if (ex != null) logger.error("Failed to persist cart CHECKOUT_FAILED status", ex);
                });
            }
        } catch (Exception ex) {
            logger.error("Error marking cart failed", ex);
        }
    }
}
