package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.CartItem;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.order.version_1.Order.OrderItem;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class BeginCheckoutProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BeginCheckoutProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public BeginCheckoutProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart BeginCheckout for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .validate(this::isValidEntity, "Invalid cart state")
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
        // Validate items
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cart has no items");
        }

        // If user present, ensure addresses exist for that user
        if (cart.getUserId() != null && !cart.getUserId().isBlank()) {
            try {
                CompletableFuture<ArrayNode> addrFuture = entityService.getItemsByCondition(
                    com.java_template.application.entity.address.version_1.Address.ENTITY_NAME,
                    String.valueOf(com.java_template.application.entity.address.version_1.Address.ENTITY_VERSION),
                    SearchConditionRequest.group("AND", Condition.of("$.userId", "EQUALS", cart.getUserId())),
                    true
                );
                ArrayNode addresses = addrFuture.get();
                if (addresses == null || addresses.size() == 0) {
                    throw new IllegalStateException("No addresses found for user " + cart.getUserId());
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error while fetching addresses for user {}", cart.getUserId(), e);
                throw new RuntimeException(e);
            }
        }

        // Inventory checks and reservation: decrement product.inventory for each item
        List<Product> updatedProducts = new ArrayList<>();

        for (CartItem item : cart.getItems()) {
            try {
                CompletableFuture<ArrayNode> productSearchFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    SearchConditionRequest.group("AND", Condition.of("$.productId", "EQUALS", item.getProductId())),
                    true
                );
                ArrayNode products = productSearchFuture.get();
                if (products == null || products.size() == 0) {
                    throw new IllegalStateException("Product not found: " + item.getProductId());
                }
                ObjectNode productNode = (ObjectNode) products.get(0);
                Product product = objectMapper.convertValue(productNode, Product.class);

                if (product.getInventory() == null || product.getInventory() < item.getQty()) {
                    throw new IllegalStateException("Insufficient inventory for product: " + item.getProductId());
                }

                // Reserve by decrementing inventory. There is no reserved field on Product in current model.
                product.setInventory(product.getInventory() - item.getQty());

                // Update product using entityService
                UUID technicalId = null;
                if (productNode.has("technicalId")) {
                    try { technicalId = UUID.fromString(productNode.get("technicalId").asText()); } catch (Exception ex) { technicalId = null; }
                }
                if (technicalId == null) {
                    // If technical id is not present, fail safe
                    throw new IllegalStateException("Product technical id missing for: " + item.getProductId());
                }

                CompletableFuture<UUID> updated = entityService.updateItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), technicalId, product);
                updated.get();
                updatedProducts.add(product);

            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error while reserving inventory for item {}", item.getProductId(), e);
                throw new RuntimeException(e);
            }
        }

        // Update cart status
        cart.setStatus("CHECKOUT_INITIATED");
        logger.info("Cart {} moved to CHECKOUT_INITIATED and reserved inventory for {} products", cart.getCartId(), updatedProducts.size());
        return cart;
    }
}
