package com.java_template.application.processor;
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

@Component
public class ReservationExpiryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReservationExpiryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReservationExpiryProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Reservation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Reservation.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract Reservation entity: {}", error.getMessage(), error);
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

    private boolean isValidEntity(Reservation entity) {
        return entity != null && entity.isValid();
    }

    private Reservation processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Reservation> context) {
        Reservation entity = context.entity();

        try {
            // Only process ACTIVE reservations
            if (entity.getStatus() != null && entity.getStatus().equalsIgnoreCase("ACTIVE")) {
                String expiresAt = entity.getExpiresAt();
                if (expiresAt != null && !expiresAt.isBlank()) {
                    Instant now = Instant.now();
                    Instant expiryInstant;
                    try {
                        expiryInstant = Instant.parse(expiresAt);
                    } catch (Exception e) {
                        logger.warn("Reservation {} has invalid expiresAt value '{}', skipping expiry check", entity.getId(), expiresAt);
                        return entity;
                    }

                    if (!now.isBefore(expiryInstant)) {
                        // Expire the reservation
                        logger.info("Expiring Reservation id={} productId={} cartId={}", entity.getId(), entity.getProductId(), entity.getCartId());
                        entity.setStatus("EXPIRED");

                        // Release inventory: update corresponding Product.availableQuantity by adding reservation qty
                        try {
                            String productId = entity.getProductId();
                            if (productId != null && !productId.isBlank()) {
                                // Use entityService.getItem with entity name/version to reliably fetch product payload
                                CompletableFuture<DataPayload> productFuture = entityService.getItem(Product.ENTITY_NAME, Product.ENTITY_VERSION, UUID.fromString(productId));
                                DataPayload payload = productFuture.get();
                                if (payload != null && payload.getData() != null) {
                                    Product product = objectMapper.treeToValue(payload.getData(), Product.class);
                                    if (product != null) {
                                        Integer current = product.getAvailableQuantity();
                                        int add = entity.getQty() != null ? entity.getQty() : 0;
                                        if (current == null) current = 0;
                                        product.setAvailableQuantity(current + add);

                                        try {
                                            entityService.updateItem(UUID.fromString(product.getId()), product).get();
                                            logger.info("Released {} units back to Product id={}. New availableQuantity={}", add, product.getId(), product.getAvailableQuantity());
                                        } catch (Exception ex) {
                                            logger.error("Failed to update Product {} when releasing reservation {}: {}", product.getId(), entity.getId(), ex.getMessage(), ex);
                                        }
                                    }
                                } else {
                                    logger.warn("Product payload for id={} not found when expiring reservation {}", productId, entity.getId());
                                }
                            }
                        } catch (Exception ex) {
                            logger.error("Failed to release inventory for reservation {}: {}", entity.getId(), ex.getMessage(), ex);
                        }
                    }
                } else {
                    logger.warn("Reservation {} has no expiresAt set; skipping expiry", entity.getId());
                }
            } else {
                logger.debug("Reservation {} status is not ACTIVE (status={}), skipping", entity.getId(), entity.getStatus());
            }
        } catch (Exception ex) {
            logger.error("Error while processing Reservation {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
        }

        return entity;
    }
}