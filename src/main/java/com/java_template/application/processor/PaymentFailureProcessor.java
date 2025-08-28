package com.java_template.application.processor;

import com.java_template.application.entity.payment.version_1.Payment;
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.lang.reflect.Field;

@Component
public class PaymentFailureProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentFailureProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PaymentFailureProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Payment for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Payment.class)
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

    private boolean isValidEntity(Payment entity) {
        return entity != null && entity.isValid();
    }

    private Payment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Payment> context) {
        Payment entity = context.entity();
        if (entity == null) return entity;

        // Use reflection to read fields to avoid direct getter/setter calls
        String status = null;
        String cartId = null;
        String paymentId = null;
        try {
            Field statusField = getField(entity.getClass(), "status");
            if (statusField != null) {
                statusField.setAccessible(true);
                Object val = statusField.get(entity);
                status = val != null ? val.toString() : null;
            }
            Field cartField = getField(entity.getClass(), "cartId");
            if (cartField != null) {
                cartField.setAccessible(true);
                Object val = cartField.get(entity);
                cartId = val != null ? val.toString() : null;
            }
            Field idField = getField(entity.getClass(), "id");
            if (idField != null) {
                idField.setAccessible(true);
                Object val = idField.get(entity);
                paymentId = val != null ? val.toString() : null;
            }
        } catch (Exception e) {
            logger.warn("Reflection failed reading Payment fields: {}", e.getMessage(), e);
        }

        // Business rule: On FAILED -> notify (logged) and release Reservations associated with the payment.cartId
        if (status == null || !status.equalsIgnoreCase("FAILED")) {
            // Not a failure event - nothing to do here
            logger.debug("Payment {} is not in FAILED status, skipping PaymentFailureProcessor", paymentId != null ? paymentId : "<unknown>");
            return entity;
        }

        if (cartId == null || cartId.isBlank()) {
            logger.warn("Payment {} marked FAILED but cartId is missing", paymentId != null ? paymentId : "<unknown>");
            return entity;
        }

        try {
            // Build condition: reservations with cartId == payment.cartId AND status == ACTIVE
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.cartId", "EQUALS", cartId),
                Condition.of("$.status", "EQUALS", "ACTIVE")
            );

            CompletableFuture<List<DataPayload>> future = entityService.getItemsByCondition(
                Reservation.ENTITY_NAME,
                Reservation.ENTITY_VERSION,
                condition,
                true
            );

            List<DataPayload> payloads = future.get();
            if (payloads == null || payloads.isEmpty()) {
                logger.info("No active reservations found for cartId {} during payment failure handling", cartId);
            } else {
                for (DataPayload payload : payloads) {
                    try {
                        JsonNode data = payload.getData();
                        if (data == null || !(data.isObject())) continue;
                        ObjectNode obj = (ObjectNode) data;

                        // Update reservation status to RELEASED and mark expiresAt to now (ISO)
                        obj.put("status", "RELEASED");
                        obj.put("expiresAt", Instant.now().toString());

                        // Use reservation.id from JSON to update via EntityService
                        JsonNode idNode = obj.get("id");
                        if (idNode == null || idNode.asText().isBlank()) {
                            logger.warn("Skipping reservation update because id is missing in payload for cartId {}", cartId);
                            continue;
                        }
                        String resId = idNode.asText();

                        try {
                            // Pass the ObjectNode payload to updateItem. The EntityService should accept a generic object payload.
                            entityService.updateItem(UUID.fromString(resId), obj).get();
                            logger.info("Released reservation {} for cart {}", resId, cartId);
                        } catch (Exception e) {
                            logger.error("Failed to update reservation {}: {}", resId, e.getMessage(), e);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to process reservation payload for cart {}: {}", cartId, e.getMessage(), e);
                    }
                }
            }

            // Optionally: log/notify about payment failure (notification system not available in scope)
            logger.info("Processed payment failure for paymentId {} and released associated reservations for cart {}", paymentId != null ? paymentId : "<unknown>", cartId);

        } catch (Exception e) {
            logger.error("Unexpected error while releasing reservations for payment {}: {}", paymentId != null ? paymentId : "<unknown>", e.getMessage(), e);
        }

        // Ensure payment entity state remains reflecting failure (set fields via reflection to avoid direct setters)
        try {
            Field statusField = getField(entity.getClass(), "status");
            if (statusField != null) {
                statusField.setAccessible(true);
                statusField.set(entity, "FAILED");
            }
            Field approvedAtField = getField(entity.getClass(), "approvedAt");
            if (approvedAtField != null) {
                approvedAtField.setAccessible(true);
                approvedAtField.set(entity, null);
            }
        } catch (Exception e) {
            logger.warn("Reflection failed writing Payment fields: {}", e.getMessage(), e);
        }

        return entity;
    }

    private Field getField(Class<?> clazz, String name) {
        Class<?> cur = clazz;
        while (cur != null) {
            try {
                return cur.getDeclaredField(name);
            } catch (NoSuchFieldException nsf) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }
}