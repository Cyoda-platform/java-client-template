package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.Line;
import com.java_template.application.entity.reservation.version_1.Reservation;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CreateReservationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateReservationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CreateReservationProcessor(SerializerFactory serializerFactory,
                                      EntityService entityService,
                                      ObjectMapper objectMapper) {
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

        // Ensure reservationBatchId exists on cart - create one if missing.
        if (cart.getReservationBatchId() == null || cart.getReservationBatchId().isBlank()) {
            cart.setReservationBatchId(UUID.randomUUID().toString());
        }

        // New TTL for reservations: now + 4 hours
        String newExpiresAt = Instant.now().plus(4, ChronoUnit.HOURS).toString();

        // For each cart line, create or update a Reservation (status ACTIVE) and refresh TTL/qty.
        if (cart.getLines() != null) {
            for (Line line : cart.getLines()) {
                if (line == null || line.getSku() == null || line.getSku().isBlank()) {
                    continue;
                }

                // Build simple search condition to find active reservation for this batch + sku
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.reservationBatchId", "EQUALS", cart.getReservationBatchId()),
                    Condition.of("$.sku", "EQUALS", line.getSku()),
                    Condition.of("$.status", "EQUALS", "ACTIVE")
                );

                try {
                    CompletableFuture<ArrayNode> foundFuture = entityService.getItemsByCondition(
                        Reservation.ENTITY_NAME,
                        String.valueOf(Reservation.ENTITY_VERSION),
                        condition,
                        true
                    );
                    ArrayNode results = foundFuture.join();

                    if (results == null || results.isEmpty()) {
                        // create new reservation
                        Reservation r = new Reservation();
                        r.setReservationId(UUID.randomUUID().toString());
                        r.setCartId(cart.getCartId());
                        r.setReservationBatchId(cart.getReservationBatchId());
                        r.setSku(line.getSku());
                        r.setQty(line.getQty());
                        r.setExpiresAt(newExpiresAt);
                        r.setStatus("ACTIVE");

                        // persist reservation (add)
                        entityService.addItem(
                            Reservation.ENTITY_NAME,
                            String.valueOf(Reservation.ENTITY_VERSION),
                            r
                        ).join();
                        logger.debug("Created reservation for cartId={} sku={} qty={}", cart.getCartId(), line.getSku(), line.getQty());
                    } else {
                        // update existing reservation: take first match
                        ObjectNode existingNode = (ObjectNode) results.get(0);
                        // Map to Reservation to reuse setters/getters
                        Reservation existing = objectMapper.treeToValue(existingNode, Reservation.class);
                        if (existing != null) {
                            existing.setQty(line.getQty());
                            existing.setExpiresAt(newExpiresAt);
                            existing.setStatus("ACTIVE");
                            // Update using technical id (assumes reservationId is stored as UUID string)
                            try {
                                entityService.updateItem(
                                    Reservation.ENTITY_NAME,
                                    String.valueOf(Reservation.ENTITY_VERSION),
                                    UUID.fromString(existing.getReservationId()),
                                    existing
                                ).join();
                                logger.debug("Updated reservation {} qty={} expiresAt={}", existing.getReservationId(), existing.getQty(), existing.getExpiresAt());
                            } catch (Exception e) {
                                // If technical id is not a UUID or update fails, attempt to add a new reservation as fallback
                                logger.warn("Failed to update reservation {} - will attempt add. Reason: {}", existing.getReservationId(), e.getMessage());
                                Reservation fallback = new Reservation();
                                fallback.setReservationId(UUID.randomUUID().toString());
                                fallback.setCartId(cart.getCartId());
                                fallback.setReservationBatchId(cart.getReservationBatchId());
                                fallback.setSku(line.getSku());
                                fallback.setQty(line.getQty());
                                fallback.setExpiresAt(newExpiresAt);
                                fallback.setStatus("ACTIVE");
                                entityService.addItem(
                                    Reservation.ENTITY_NAME,
                                    String.valueOf(Reservation.ENTITY_VERSION),
                                    fallback
                                ).join();
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Error while creating/updating reservation for cart {} sku {}: {}", cart.getCartId(), line.getSku(), ex.getMessage(), ex);
                }
            }
        }

        // Recalculate cart totals (totalItems and grandTotal)
        int totalItems = 0;
        double grandTotal = 0.0;
        if (cart.getLines() != null) {
            for (Line l : cart.getLines()) {
                if (l == null) continue;
                Integer qty = l.getQty() != null ? l.getQty() : 0;
                Double price = l.getPrice() != null ? l.getPrice() : 0.0;
                totalItems += qty;
                grandTotal += price * qty;
            }
        }
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);

        // The cart entity will be persisted automatically by Cyoda; return modified cart.
        return cart;
    }
}