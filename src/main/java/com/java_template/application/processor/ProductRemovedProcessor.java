package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ProductRemovedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductRemovedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ProductRemovedProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ProductRemoved for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        try {
            // If product has no id, nothing to cleanup
            if (product.getId() == null || product.getId().isBlank()) {
                logger.warn("ProductRemovedProcessor: Product id is blank, skipping cleanup");
                return product;
            }

            String productId = product.getId();

            // Business rule: mark product unavailable and zero stock when removed
            product.setAvailable(false);
            product.setStock(0);

            // Attempt to find carts referencing this product and remove the product from their items.
            // Use a simple search condition on items' productId. Search condition structure depends on platform,
            // try the common JSON path used in other processors.
            SearchConditionRequest searchRequest = SearchConditionRequest.group("AND",
                Condition.of("$.items[*].productId", "EQUALS", productId)
            );

            // Perform the search asynchronously; don't block processor flow.
            try {
                CompletableFuture<ArrayNode> searchFuture = entityService.getItemsByCondition(
                    Cart.ENTITY_NAME,
                    String.valueOf(Cart.ENTITY_VERSION),
                    searchRequest,
                    true
                );

                searchFuture.thenAccept(results -> {
                    if (results == null) return;
                    for (int i = 0; i < results.size(); i++) {
                        ObjectNode cartNode = (ObjectNode) results.get(i);
                        // The search result may include a technicalId / technical_id field - attempt both.
                        String technicalIdStr = null;
                        if (cartNode.has("technicalId") && !cartNode.get("technicalId").isNull()) {
                            technicalIdStr = cartNode.get("technicalId").asText(null);
                        } else if (cartNode.has("technical_id") && !cartNode.get("technical_id").isNull()) {
                            technicalIdStr = cartNode.get("technical_id").asText(null);
                        } else if (cartNode.has("id") && !cartNode.get("id").isNull()) {
                            // Fallback: if only id is present and it's a UUID string, use it.
                            technicalIdStr = cartNode.get("id").asText(null);
                        }

                        if (technicalIdStr == null) {
                            logger.warn("ProductRemovedProcessor: cannot determine technicalId for cart search result: {}", cartNode);
                            continue;
                        }

                        try {
                            UUID technicalId = UUID.fromString(technicalIdStr);
                            // Read latest cart item
                            CompletableFuture<ObjectNode> cartFuture = entityService.getItem(
                                Cart.ENTITY_NAME,
                                String.valueOf(Cart.ENTITY_VERSION),
                                technicalId
                            );

                            cartFuture.thenAccept(cartPayload -> {
                                if (cartPayload == null) return;
                                // Remove items that match productId and recalculate total
                                boolean changed = false;
                                double sum = 0.0;
                                if (cartPayload.has("items") && cartPayload.get("items").isArray()) {
                                    ArrayNode items = (ArrayNode) cartPayload.get("items");
                                    // Remove matching items by iterating and removing
                                    Iterator<com.fasterxml.jackson.databind.JsonNode> iter = items.iterator();
                                    while (iter.hasNext()) {
                                        com.fasterxml.jackson.databind.JsonNode it = iter.next();
                                        if (it != null && it.has("productId") && !it.get("productId").isNull()
                                            && productId.equals(it.get("productId").asText(null))) {
                                            // remove this item
                                            iter.remove();
                                            changed = true;
                                        }
                                    }
                                    // Recalculate total from remaining items
                                    for (int idx = 0; idx < items.size(); idx++) {
                                        com.fasterxml.jackson.databind.JsonNode rem = items.get(idx);
                                        if (rem != null && rem.has("priceAtAdd") && rem.has("quantity")
                                            && !rem.get("priceAtAdd").isNull() && !rem.get("quantity").isNull()) {
                                            try {
                                                double price = rem.get("priceAtAdd").asDouble(0.0);
                                                int qty = rem.get("quantity").asInt(0);
                                                sum += price * qty;
                                            } catch (Exception ex) {
                                                // ignore malformed item
                                            }
                                        }
                                    }
                                }

                                if (changed) {
                                    cartPayload.put("total", sum);
                                    // Persist updated cart via updateItem (use the technicalId)
                                    try {
                                        entityService.updateItem(
                                            Cart.ENTITY_NAME,
                                            String.valueOf(Cart.ENTITY_VERSION),
                                            technicalId,
                                            cartPayload
                                        ).whenComplete((u, ex) -> {
                                            if (ex != null) {
                                                logger.error("Failed to update cart {} after removing product {}: {}", technicalId, productId, ex.getMessage(), ex);
                                            } else {
                                                logger.info("Updated cart {} after removing product {}", technicalId, productId);
                                            }
                                        });
                                    } catch (Exception ex) {
                                        logger.error("Failed to schedule cart update for cart {}: {}", technicalId, ex.getMessage(), ex);
                                    }
                                }
                            }).exceptionally(ex -> {
                                logger.error("Error fetching cart {} for cleanup: {}", technicalIdStr, ex.getMessage(), ex);
                                return null;
                            });
                        } catch (IllegalArgumentException iae) {
                            logger.warn("ProductRemovedProcessor: cart technicalId '{}' is not a valid UUID, skipping", technicalIdStr);
                        } catch (Exception ex) {
                            logger.error("Unexpected error while processing cart search result: {}", ex.getMessage(), ex);
                        }
                    }
                }).exceptionally(ex -> {
                    logger.error("Error during cart search for product {}: {}", productId, ex.getMessage(), ex);
                    return null;
                });
            } catch (NoSuchMethodError nsme) {
                // If the search method is not available on EntityService for some environments, log and skip cart cleanup.
                logger.warn("EntityService.search(...) not available; skipping cart cleanup for product {}. Error: {}", productId, nsme.getMessage());
            } catch (Exception ex) {
                logger.error("Failed to initiate cart cleanup for product {}: {}", productId, ex.getMessage(), ex);
            }

        } catch (Exception ex) {
            logger.error("Unexpected error during ProductRemovedProcessor processing for product {}: {}", product != null ? product.getId() : "unknown", ex.getMessage(), ex);
        }

        return product;
    }
}