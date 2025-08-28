package com.java_template.application.processor;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.reservation.version_1.Reservation;
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
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

@Component
public class ProductDeactivateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductDeactivateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProductDeactivateProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

    private boolean isValidEntity(Product entity) {
        return entity != null && entity.isValid();
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product entity = context.entity();
        if (entity == null) {
            logger.warn("Received null Product entity in processing context.");
            return entity;
        }

        logger.info("Deactivating Product id={}, sku={}", entity.getId(), entity.getSku());

        // Business rule: mark product as unavailable by setting availableQuantity to 0.
        // We must keep the entity valid according to its isValid() implementation,
        // so only change fields that do not make entity invalid.
        try {
            entity.setAvailableQuantity(0);
        } catch (Exception e) {
            logger.error("Failed to set availableQuantity to 0 for product {}: {}", entity.getId(), e.getMessage(), e);
        }

        // Additional business rule: release any ACTIVE reservations for this product.
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.productId", "EQUALS", entity.getId()),
                Condition.of("$.status", "EQUALS", "ACTIVE")
            );

            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                Reservation.ENTITY_NAME,
                Reservation.ENTITY_VERSION,
                condition,
                true
            );

            List<DataPayload> dataPayloads = itemsFuture.get();

            if (dataPayloads != null && !dataPayloads.isEmpty()) {
                for (DataPayload payload : dataPayloads) {
                    try {
                        Reservation reservation = objectMapper.treeToValue(payload.getData(), Reservation.class);
                        if (reservation == null) continue;

                        // Only update other entities via EntityService. Do not call update on the triggering Product.
                        // Mark reservation as RELEASED (manual release due to product deactivation).
                        reservation.setStatus("RELEASED");

                        // Ensure reservation remains valid (we didn't clear required fields).
                        if (!reservation.isValid()) {
                            logger.warn("Skipping update of reservation {} because it would become invalid after change.", reservation.getId());
                            continue;
                        }

                        // Use reservation.getId() as technical id to update
                        if (reservation.getId() != null && !reservation.getId().isBlank()) {
                            CompletableFuture<java.util.UUID> updateFuture = entityService.updateItem(
                                UUID.fromString(reservation.getId()),
                                reservation
                            );
                            UUID updatedId = updateFuture.get();
                            logger.info("Released reservation {} (updated technicalId={}) due to product deactivation {}", reservation.getId(), updatedId, entity.getId());
                        } else {
                            logger.warn("Reservation missing id field, cannot update: {}", reservation);
                        }
                    } catch (Exception innerEx) {
                        logger.error("Failed processing reservation payload for product {}: {}", entity.getId(), innerEx.getMessage(), innerEx);
                    }
                }
            } else {
                logger.debug("No ACTIVE reservations found for product {}", entity.getId());
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching reservations for product {}: {}", entity.getId(), ie.getMessage(), ie);
        } catch (ExecutionException ee) {
            logger.error("Execution error while fetching reservations for product {}: {}", entity.getId(), ee.getMessage(), ee);
        } catch (Exception e) {
            logger.error("Unexpected error while releasing reservations for product {}: {}", entity.getId(), e.getMessage(), e);
        }

        // Return modified product entity; Cyoda will persist changes for the triggering entity.
        return entity;
    }
}