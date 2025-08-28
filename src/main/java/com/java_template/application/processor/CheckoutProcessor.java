package com.java_template.application.processor;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.cart.version_1.Cart.CartItem;
import com.java_template.application.entity.reservation.version_1.Reservation;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import static com.java_template.common.config.Config.*;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

@Component
public class CheckoutProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CheckoutProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        try {
            // Guard: cart items must not be empty (Cart.isValid already enforces this),
            // and all reservations for this cart must be ACTIVE for the requested qty.
            // Fetch reservations for this cart
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.cartId", "EQUALS", cart.getId())
            );

            CompletableFuture<List<DataPayload>> reservationsFuture = entityService.getItemsByCondition(
                    Reservation.ENTITY_NAME,
                    Reservation.ENTITY_VERSION,
                    condition,
                    true
            );

            List<DataPayload> reservationPayloads = reservationsFuture.get();
            List<Reservation> reservations = new ArrayList<>();
            if (reservationPayloads != null) {
                for (DataPayload payload : reservationPayloads) {
                    try {
                        Reservation r = objectMapper.treeToValue(payload.getData(), Reservation.class);
                        if (r != null) reservations.add(r);
                    } catch (Exception e) {
                        logger.warn("Failed to deserialize reservation payload for cart {}: {}", cart.getId(), e.getMessage());
                    }
                }
            }

            // Build map of available reserved qty per product where reservation.status == ACTIVE
            Map<String, Integer> reservedAvailable = new HashMap<>();
            for (Reservation r : reservations) {
                if (r.getStatus() != null && "ACTIVE".equalsIgnoreCase(r.getStatus())) {
                    Integer prev = reservedAvailable.getOrDefault(r.getProductId(), 0);
                    Integer add = r.getQty() != null ? r.getQty() : 0;
                    reservedAvailable.put(r.getProductId(), prev + add);
                }
            }

            // Check each cart item against reservations
            boolean allSatisfied = true;
            double payableAmount = 0.0;
            for (CartItem item : cart.getItems()) {
                String pid = item.getProductId();
                Integer qty = item.getQty();
                Double price = item.getPriceSnapshot();
                int reservedQty = reservedAvailable.getOrDefault(pid, 0);
                if (qty == null || qty <= 0) {
                    allSatisfied = false;
                    break;
                }
                if (reservedQty < qty) {
                    allSatisfied = false;
                    break;
                }
                // accumulate payable amount for items that are reserved (available)
                if (price != null) {
                    payableAmount += price * qty;
                }
            }

            if (!allSatisfied) {
                // Guard failed: do not proceed to checkout. Keep cart state unchanged and log.
                logger.info("Checkout guard failed for cart {}: items missing or reservations not ACTIVE", cart.getId());
                // Surface UI message via logging (no explicit field available on Cart)
                return cart;
            }

            // Guard passed: create Payment (PENDING) for available items only
            Payment payment = new Payment();
            payment.setId(UUID.randomUUID().toString());
            payment.setCartId(cart.getId());
            payment.setUserId(cart.getUserId());
            payment.setAmount(payableAmount);
            payment.setStatus("PENDING");
            payment.setCreatedAt(Instant.now().toString());
            payment.setApprovedAt(null);
            payment.setOrderId(null);

            try {
                CompletableFuture<java.util.UUID> added = entityService.addItem(
                        Payment.ENTITY_NAME,
                        Payment.ENTITY_VERSION,
                        payment
                );
                java.util.UUID paymentId = added.get();
                logger.info("Created payment {} for cart {} (entityId: {})", payment.getId(), cart.getId(), paymentId);
            } catch (Exception e) {
                logger.error("Failed to create Payment for cart {}: {}", cart.getId(), e.getMessage(), e);
                // Do not change cart status if payment creation fails
                return cart;
            }

            // Update cart status to CHECKED_OUT and update lastUpdated
            cart.setStatus("CHECKED_OUT");
            cart.setLastUpdated(Instant.now().toString());

        } catch (Exception ex) {
            logger.error("Error in CheckoutProcessor for cart {}: {}", cart != null ? cart.getId() : "null", ex.getMessage(), ex);
            // On unexpected errors, return entity unchanged
        }

        return cart;
    }
}