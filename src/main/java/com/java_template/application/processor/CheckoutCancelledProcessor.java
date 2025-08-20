package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class CheckoutCancelledProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutCancelledProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CheckoutCancelledProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CheckoutCancelled for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart for checkout cancellation")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart cart) {
        return cart != null && cart.isValid();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();
        // Release reservations and set status back to OPEN
        if (cart.getItems() != null) {
            for (com.java_template.application.entity.cart.version_1.Cart.CartItem item : cart.getItems()) {
                try {
                    CompletableFuture<ArrayNode> productSearchFuture = entityService.getItemsByCondition(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        SearchConditionRequest.group("AND", Condition.of("$.productId", "EQUALS", item.getProductId())),
                        true
                    );
                    ArrayNode products = productSearchFuture.get();
                    if (products != null && products.size() > 0) {
                        ObjectNode productNode = (ObjectNode) products.get(0);
                        Product product = objectMapper.convertValue(productNode, Product.class);
                        product.setInventory(product.getInventory() + item.getQty());
                        if (productNode.has("technicalId")) {
                            try {
                                entityService.updateItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), java.util.UUID.fromString(productNode.get("technicalId").asText()), product).get();
                            } catch (Exception ex) {
                                logger.warn("Failed to update product inventory during checkout cancel for {}", product.getProductId(), ex);
                            }
                        }
                    }
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Error while releasing inventory for cancelled checkout cart {}", cart.getCartId(), e);
                }
            }
        }

        cart.setStatus("OPEN");
        logger.info("Cart {} checkout cancelled and inventory released", cart.getCartId());
        return cart;
    }
}
