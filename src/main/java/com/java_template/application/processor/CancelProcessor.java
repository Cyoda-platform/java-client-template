package com.java_template.application.processor;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.reservation.version_1.Reservation;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class CancelProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CancelProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CancelProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        if (cart == null) return cart;

        try {
            // Only proceed if cart is not already cancelled
            if ("CANCELLED".equalsIgnoreCase(cart.getStatus())) {
                logger.info("Cart {} is already CANCELLED; no action required.", cart.getId());
                return cart;
            }

            // Build condition to find reservations tied to this cart
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.cartId", "EQUALS", cart.getId())
            );

            CompletableFuture<List<DataPayload>> filteredItemsFuture =
                entityService.getItemsByCondition(
                    Reservation.ENTITY_NAME,
                    Reservation.ENTITY_VERSION,
                    condition,
                    true
                );

            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    try {
                        JsonNode data = payload.getData();
                        Reservation reservation = objectMapper.treeToValue(data, Reservation.class);
                        if (reservation == null) continue;

                        String resStatus = reservation.getStatus();
                        // If reservation is active (or not already released/expired), release it
                        if (resStatus == null || (!"RELEASED".equalsIgnoreCase(resStatus) && !"EXPIRED".equalsIgnoreCase(resStatus))) {
                            reservation.setStatus("RELEASED");
                            reservation.setExpiresAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

                            // Persist reservation update (allowed: updating other entities)
                            try {
                                if (reservation.getId() != null && !reservation.getId().isBlank()) {
                                    entityService.updateItem(UUID.fromString(reservation.getId()), reservation).get();
                                    logger.info("Released reservation {}", reservation.getId());
                                } else {
                                    logger.warn("Skipping update for reservation without technical id (cartId={} productId={})", reservation.getCartId(), reservation.getProductId());
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                logger.error("Failed to update reservation {}: {}", reservation.getId(), e.getMessage(), e);
                            }

                            // Attempt to return reserved qty to Product.availableQuantity
                            try {
                                if (reservation.getProductId() != null && !reservation.getProductId().isBlank()) {
                                    // Use entityService.getItem with explicit entity name/version to fetch Product payload
                                    CompletableFuture<DataPayload> prodFuture = entityService.getItem(
                                        Product.ENTITY_NAME,
                                        Product.ENTITY_VERSION,
                                        UUID.fromString(reservation.getProductId())
                                    );
                                    DataPayload prodPayload = prodFuture.get();
                                    if (prodPayload != null && prodPayload.getData() != null) {
                                        Product product = objectMapper.treeToValue(prodPayload.getData(), Product.class);
                                        if (product != null) {
                                            Integer avail = product.getAvailableQuantity();
                                            if (avail == null) avail = 0;
                                            Integer addQty = reservation.getQty() == null ? 0 : reservation.getQty();
                                            product.setAvailableQuantity(avail + addQty);

                                            try {
                                                if (product.getId() != null && !product.getId().isBlank()) {
                                                    entityService.updateItem(UUID.fromString(product.getId()), product).get();
                                                    logger.info("Restored {} units to product {}", addQty, product.getId());
                                                } else {
                                                    logger.warn("Product payload missing technical id for product reference {}. Skipping update.", reservation.getProductId());
                                                }
                                            } catch (InterruptedException | ExecutionException e) {
                                                logger.error("Failed to update product {}: {}", product.getId(), e.getMessage(), e);
                                            }
                                        }
                                    } else {
                                        logger.warn("Product payload for id={} not found when releasing reservation {}", reservation.getProductId(), reservation.getId());
                                    }
                                }
                            } catch (InterruptedException | ExecutionException ie) {
                                logger.error("Failed to fetch product for reservation {}: {}", reservation.getId(), ie.getMessage(), ie);
                            }
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to process reservation payload: {}", ex.getMessage(), ex);
                    }
                }
            }

            // Update cart state to CANCELLED (the triggering entity is modified directly; Cyoda will persist it)
            cart.setStatus("CANCELLED");
            cart.setLastUpdated(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            logger.info("Cart {} marked as CANCELLED", cart.getId());

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while releasing reservations for cart {}: {}", cart.getId(), e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error in CancelProcessor for cart {}: {}", cart.getId(), e.getMessage(), e);
        }

        return cart;
    }
}