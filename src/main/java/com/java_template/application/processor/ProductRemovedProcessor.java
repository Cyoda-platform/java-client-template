package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ProductRemovedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductRemovedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ProductRemovedProcessor(SerializerFactory serializerFactory,
                                   EntityService entityService,
                                   ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Product entity) {
        return entity != null && entity.isValid();
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();

        // Business intent:
        // - When a product is removed (ProductRemovedProcessor), clean up references in other entities.
        // - Specifically, find carts that reference this product and remove the items from those carts,
        //   then recalculate cart totals and update those cart entities via EntityService.
        // - Do not call update on Product via EntityService; adjust the Product instance and return it
        //   so Cyoda persists it as part of the workflow.
        try {
            if (product.getId() == null || product.getId().isBlank()) {
                logger.warn("Product id is blank, skipping cleanup");
                return product;
            }

            String productId = product.getId();

            // Build search condition to find carts that reference this product in items array.
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.items[*].productId", "EQUALS", productId)
            );

            CompletableFuture<ArrayNode> cartsFuture = entityService.getItemsByCondition(
                Cart.ENTITY_NAME,
                String.valueOf(Cart.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode cartsArray = cartsFuture.join();
            if (cartsArray == null || cartsArray.isEmpty()) {
                logger.info("No carts found referencing productId={}", productId);
            } else {
                logger.info("Found {} cart(s) referencing productId={}", cartsArray.size(), productId);
                for (int i = 0; i < cartsArray.size(); i++) {
                    ObjectNode cartNode = (ObjectNode) cartsArray.get(i);
                    try {
                        Cart cart = objectMapper.treeToValue(cartNode, Cart.class);
                        if (cart == null) continue;

                        List<Cart.Item> items = cart.getItems();
                        boolean modified = false;

                        if (items != null && !items.isEmpty()) {
                            Iterator<Cart.Item> it = items.iterator();
                            while (it.hasNext()) {
                                Cart.Item itItem = it.next();
                                if (itItem != null && productId.equals(itItem.getProductId())) {
                                    // Remove the item that references the removed product
                                    it.remove();
                                    modified = true;
                                }
                            }
                        }

                        if (modified) {
                            // Recalculate total
                            double sum = 0.0;
                            if (cart.getItems() != null) {
                                for (Cart.Item remaining : cart.getItems()) {
                                    if (remaining != null && remaining.getPriceAtAdd() != null && remaining.getQuantity() != null) {
                                        sum += remaining.getPriceAtAdd() * remaining.getQuantity();
                                    }
                                }
                            }
                            cart.setTotal(sum);

                            // Update the Cart entity (allowed: updating other entities)
                            try {
                                UUID technicalId = UUID.fromString(cart.getId());
                                CompletableFuture<UUID> updateFuture = entityService.updateItem(
                                    Cart.ENTITY_NAME,
                                    String.valueOf(Cart.ENTITY_VERSION),
                                    technicalId,
                                    cart
                                );
                                updateFuture.join();
                                logger.info("Updated cart {} after removing product {}", cart.getId(), productId);
                            } catch (Exception e) {
                                logger.error("Failed to update cart {}: {}", cart.getId(), e.getMessage(), e);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Failed to process cart node while cleaning product {}: {}", productId, e.getMessage(), e);
                    }
                }
            }

            // Adjust the Product instance state so Cyoda persists the removed state.
            // Mark as not available and zero stock to indicate removal.
            product.setAvailable(false);
            product.setStock(0);

            logger.info("Completed cleanup for removed product {}", productId);
        } catch (Exception ex) {
            logger.error("Unexpected error during ProductRemovedProcessor for product {}: {}", product.getId(), ex.getMessage(), ex);
        }

        return product;
    }
}