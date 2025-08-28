package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.reservation.version_1.Reservation;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ReleaseProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReleaseProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ReleaseProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Only act when a cart is being released/cancelled — release associated reservations
        String status = cart.getStatus();
        if (status == null) return cart;

        boolean shouldRelease = "RELEASED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status);
        if (!shouldRelease) {
            // Nothing to do
            return cart;
        }

        try {
            // Build condition: reservations where cartId == cart.id
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.cartId", "EQUALS", cart.getId())
            );

            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                Reservation.ENTITY_NAME,
                Reservation.ENTITY_VERSION,
                condition,
                true
            );

            List<DataPayload> dataPayloads = itemsFuture.get();
            if (dataPayloads == null || dataPayloads.isEmpty()) {
                logger.info("No reservations found for cartId={}", cart.getId());
                return cart;
            }

            for (DataPayload payload : dataPayloads) {
                try {
                    Reservation reservation = objectMapper.treeToValue(payload.getData(), Reservation.class);
                    if (reservation == null) continue;

                    // If reservation is already released, skip
                    String resStatus = reservation.getStatus();
                    if ("RELEASED".equalsIgnoreCase(resStatus)) continue;

                    // Mark reservation as RELEASED and update expiry timestamp
                    reservation.setStatus("RELEASED");
                    reservation.setExpiresAt(Instant.now().toString());

                    // Update reservation via EntityService (we are allowed to update other entities)
                    try {
                        entityService.updateItem(UUID.fromString(reservation.getId()), reservation).get();
                        logger.info("Released reservation id={} for cartId={}", reservation.getId(), cart.getId());
                    } catch (Exception e) {
                        logger.error("Failed to update reservation id={}: {}", reservation.getId(), e.getMessage(), e);
                    }
                } catch (Exception e) {
                    logger.error("Failed to process reservation payload for cartId={}: {}", cart.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception ex) {
            logger.error("Error while releasing reservations for cartId={}: {}", cart.getId(), ex.getMessage(), ex);
        }

        // Return the cart entity unchanged (Cyoda will persist the triggering entity state automatically if needed)
        return cart;
    }
}