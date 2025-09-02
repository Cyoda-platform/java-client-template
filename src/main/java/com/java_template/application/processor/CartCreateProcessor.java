package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CartCreateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartCreateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    @Autowired
    private ObjectMapper objectMapper;

    public CartCreateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart entity) {
        return entity != null && entity.isValid();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart cart = context.entity();

        try {
            // Generate unique cartId if not present
            if (cart.getCartId() == null || cart.getCartId().trim().isEmpty()) {
                cart.setCartId("cart-" + UUID.randomUUID().toString());
            }

            // Get input data from request payload
            String sku = (String) context.getInputData().get("sku");
            Integer qty = (Integer) context.getInputData().get("qty");

            if (sku == null || qty == null || qty <= 0) {
                throw new IllegalArgumentException("Invalid input: sku and positive qty are required");
            }

            // Fetch product by SKU
            CompletableFuture<Product> productFuture = getProductBySku(sku);
            Product product = productFuture.join();

            if (product == null) {
                throw new IllegalArgumentException("Product not found for SKU: " + sku);
            }

            // Create first line item
            Cart.CartLine line = new Cart.CartLine();
            line.setSku(sku);
            line.setName(product.getName());
            line.setPrice(product.getPrice());
            line.setQty(qty);

            List<Cart.CartLine> lines = new ArrayList<>();
            lines.add(line);
            cart.setLines(lines);

            // Calculate totals
            cart.setTotalItems(qty);
            cart.setGrandTotal(product.getPrice() * qty);

            // Set timestamps
            Instant now = Instant.now();
            cart.setCreatedAt(now);
            cart.setUpdatedAt(now);

            logger.info("Created cart {} with first item {}", cart.getCartId(), sku);
            return cart;

        } catch (Exception e) {
            logger.error("Error processing cart creation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create cart: " + e.getMessage(), e);
        }
    }

    private CompletableFuture<Product> getProductBySku(String sku) {
        Condition skuCondition = Condition.of("$.sku", "EQUALS", sku);
        SearchConditionRequest condition = new SearchConditionRequest();
        condition.setType("group");
        condition.setOperator("AND");
        condition.setConditions(List.of(skuCondition));

        return entityService.getFirstItemByCondition(
            Product.ENTITY_NAME,
            Product.ENTITY_VERSION,
            condition,
            true
        ).thenApply(optionalPayload -> {
            if (optionalPayload.isPresent()) {
                try {
                    return objectMapper.convertValue(optionalPayload.get().getData(), Product.class);
                } catch (Exception e) {
                    logger.error("Error converting product data: {}", e.getMessage(), e);
                    return null;
                }
            }
            return null;
        });
    }
}