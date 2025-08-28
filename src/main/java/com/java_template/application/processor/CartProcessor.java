package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.CartItem;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.reservation.version_1.Reservation;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;

@Component
public class CartProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CartProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CartProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // For each cart item, create or update Reservation(s) based on available Product inventory.
        // Reservation TTL is 30 seconds from now; status = "ACTIVE".
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            logger.info("Cart {} has no items; no reservations created.", cart.getId());
            return cart;
        }

        for (CartItem item : cart.getItems()) {
            if (item == null) continue;
            String productId = item.getProductId();
            Integer requestedQty = item.getQty() != null ? item.getQty() : 0;
            if (productId == null || productId.isBlank() || requestedQty <= 0) {
                logger.warn("Skipping invalid cart item for cart {}: productId={}, qty={}", cart.getId(), productId, requestedQty);
                continue;
            }

            try {
                // Find product by id
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", productId)
                );
                CompletableFuture<List<DataPayload>> productsFuture = entityService.getItemsByCondition(
                        Product.ENTITY_NAME,
                        Product.ENTITY_VERSION,
                        condition,
                        true
                );
                List<DataPayload> productPayloads = productsFuture.get();
                if (productPayloads == null || productPayloads.isEmpty()) {
                    logger.warn("Product {} not found for cart {} item; skipping reservation.", productId, cart.getId());
                    continue;
                }

                // Take first matching product
                JsonNode prodNode = productPayloads.get(0).getData();
                Product product = objectMapper.treeToValue(prodNode, Product.class);
                if (product == null || !product.isValid()) {
                    logger.warn("Product {} is invalid; skipping reservation for cart {}", productId, cart.getId());
                    continue;
                }

                int available = product.getAvailableQuantity() != null ? product.getAvailableQuantity() : 0;
                int allocateQty = Math.min(available, requestedQty);
                if (allocateQty <= 0) {
                    logger.info("No available inventory for product {} (requested={}, available={})", productId, requestedQty, available);
                    // No reservation created if nothing to allocate
                    continue;
                }

                // Check existing reservations for this cart + product
                SearchConditionRequest existingCond = SearchConditionRequest.group("AND",
                        Condition.of("$.cartId", "EQUALS", cart.getId()),
                        Condition.of("$.productId", "EQUALS", productId)
                );
                CompletableFuture<List<DataPayload>> existingFuture = entityService.getItemsByCondition(
                        Reservation.ENTITY_NAME,
                        Reservation.ENTITY_VERSION,
                        existingCond,
                        true
                );
                List<DataPayload> existingPayloads = existingFuture.get();

                Instant now = Instant.now();
                String nowIso = DateTimeFormatter.ISO_INSTANT.format(now);
                String expiresIso = DateTimeFormatter.ISO_INSTANT.format(now.plus(30, ChronoUnit.SECONDS));

                if (existingPayloads != null && !existingPayloads.isEmpty()) {
                    // Update first existing reservation to refresh qty/status/expiry
                    DataPayload payload = existingPayloads.get(0);
                    JsonNode node = payload.getData();
                    Reservation existing = objectMapper.treeToValue(node, Reservation.class);
                    if (existing != null) {
                        existing.setQty(allocateQty);
                        existing.setWarehouseId(product.getWarehouseId());
                        existing.setStatus("ACTIVE");
                        existing.setCreatedAt(nowIso); // refresh createdAt to now (business choice)
                        existing.setExpiresAt(expiresIso);
                        try {
                            CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(existing.getId()), existing);
                            UUID updatedId = updated.get();
                            logger.info("Updated reservation {} for cart {} product {} allocatedQty={}", updatedId, cart.getId(), productId, allocateQty);
                        } catch (InterruptedException | ExecutionException ue) {
                            logger.error("Failed to update reservation for cart {} product {}: {}", cart.getId(), productId, ue.getMessage(), ue);
                        }
                    }
                    // If multiple existing reservations exist, we leave extras intact (or could cleanup) - keep behavior simple.
                } else {
                    // Create new reservation
                    Reservation reservation = new Reservation();
                    reservation.setId(UUID.randomUUID().toString());
                    reservation.setCartId(cart.getId());
                    reservation.setProductId(productId);
                    reservation.setWarehouseId(product.getWarehouseId());
                    reservation.setQty(allocateQty);
                    reservation.setStatus("ACTIVE");
                    reservation.setCreatedAt(nowIso);
                    reservation.setExpiresAt(expiresIso);
                    try {
                        CompletableFuture<UUID> addFuture = entityService.addItem(
                                Reservation.ENTITY_NAME,
                                Reservation.ENTITY_VERSION,
                                reservation
                        );
                        UUID resId = addFuture.get();
                        logger.info("Created reservation {} for cart {} product {} qty={}", resId, cart.getId(), productId, allocateQty);
                    } catch (InterruptedException | ExecutionException ae) {
                        logger.error("Failed to create reservation for cart {} product {}: {}", cart.getId(), productId, ae.getMessage(), ae);
                    }
                }

            } catch (InterruptedException | ExecutionException ex) {
                logger.error("Error while processing reservations for cart {} product {}: {}", cart.getId(), productId, ex.getMessage(), ex);
                // swallow exception to continue processing other items
            } catch (Exception e) {
                logger.error("Unexpected error processing cart {} item {}: {}", cart.getId(), productId, e.getMessage(), e);
            }
        }

        // No modifications to the cart entity fields are strictly required here;
        // if desired, we could refresh lastUpdated timestamp:
        try {
            String nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            cart.setLastUpdated(nowIso);
        } catch (Exception e) {
            logger.debug("Unable to update cart.lastUpdated for cart {}: {}", cart.getId(), e.getMessage());
        }

        return cart;
    }
}