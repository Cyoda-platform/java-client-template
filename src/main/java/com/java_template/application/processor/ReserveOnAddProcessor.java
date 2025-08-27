package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class ReserveOnAddProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReserveOnAddProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ReserveOnAddProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        // Ensure reservation batch exists
        if (cart.getReservationBatchId() == null || cart.getReservationBatchId().isBlank()) {
            cart.setReservationBatchId(UUID.randomUUID().toString());
        }

        // Recalculate totals based on lines (totalItems and grandTotal)
        List<Line> lines = cart.getLines();
        int totalItems = 0;
        double grandTotal = 0d;
        if (lines != null) {
            for (Line l : lines) {
                if (l != null && l.getQty() != null && l.getPrice() != null) {
                    totalItems += l.getQty();
                    grandTotal += l.getQty() * l.getPrice();
                }
            }
        }
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);

        // For each cart line, create or update reservation and refresh TTL (4h)
        if (lines != null) {
            for (Line l : lines) {
                if (l == null || l.getSku() == null || l.getSku().isBlank()) {
                    continue;
                }
                try {
                    // Build search condition for existing ACTIVE reservation matching batch and sku for this cart
                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.reservationBatchId", "EQUALS", cart.getReservationBatchId()),
                        Condition.of("$.sku", "EQUALS", l.getSku()),
                        Condition.of("$.cartId", "EQUALS", cart.getCartId()),
                        Condition.of("$.status", "EQUALS", "ACTIVE")
                    );

                    CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                        Reservation.ENTITY_NAME,
                        String.valueOf(Reservation.ENTITY_VERSION),
                        condition,
                        true
                    );

                    ArrayNode results = itemsFuture.join();

                    String newExpiry = DateTimeFormatter.ISO_INSTANT.format(Instant.now().plus(4, ChronoUnit.HOURS));

                    if (results == null || results.size() == 0) {
                        // Create new reservation
                        Reservation res = new Reservation();
                        res.setReservationId(UUID.randomUUID().toString());
                        res.setCartId(cart.getCartId());
                        res.setReservationBatchId(cart.getReservationBatchId());
                        res.setSku(l.getSku());
                        res.setQty(l.getQty());
                        res.setStatus("ACTIVE");
                        res.setExpiresAt(newExpiry);

                        entityService.addItem(
                            Reservation.ENTITY_NAME,
                            String.valueOf(Reservation.ENTITY_VERSION),
                            res
                        ).join();
                    } else {
                        // Update first matching reservation: qty and expiresAt
                        JsonNode first = results.get(0);
                        Reservation existing = objectMapper.treeToValue(first, Reservation.class);
                        if (existing.getReservationId() != null && !existing.getReservationId().isBlank()) {
                            existing.setQty(l.getQty());
                            existing.setExpiresAt(newExpiry);
                            // keep status ACTIVE
                            existing.setStatus("ACTIVE");
                            entityService.updateItem(
                                Reservation.ENTITY_NAME,
                                String.valueOf(Reservation.ENTITY_VERSION),
                                UUID.fromString(existing.getReservationId()),
                                existing
                            ).join();
                        } else {
                            // Fallback: create new reservation if existing record malformed
                            Reservation res = new Reservation();
                            res.setReservationId(UUID.randomUUID().toString());
                            res.setCartId(cart.getCartId());
                            res.setReservationBatchId(cart.getReservationBatchId());
                            res.setSku(l.getSku());
                            res.setQty(l.getQty());
                            res.setStatus("ACTIVE");
                            res.setExpiresAt(newExpiry);

                            entityService.addItem(
                                Reservation.ENTITY_NAME,
                                String.valueOf(Reservation.ENTITY_VERSION),
                                res
                            ).join();
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Error while creating/updating reservation for cart {} sku {}: {}", cart.getCartId(), l.getSku(), ex.getMessage(), ex);
                    // Do not throw; best-effort reservation processing. Cart will still be persisted.
                }
            }
        }

        return cart;
    }
}