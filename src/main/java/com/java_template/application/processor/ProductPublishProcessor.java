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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;
import java.util.ArrayList;

@Component
public class ProductPublishProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductPublishProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProductPublishProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        Product product = context.entity();

        // Business rules implemented:
        // - When a Product is created/updated, validate inventory and try to satisfy ACTIVE Reservations for this product.
        // - Iterate ACTIVE reservations ordered by retrieval (FIFO by stored order), allocate availableQuantity to reservations.
        //   * If availableQuantity covers reservation.qty -> leave reservation ACTIVE (considered satisfied) and reduce availableQuantity.
        //   * If availableQuantity cannot cover a reservation -> expire that reservation (set status = "EXPIRED") and persist update.
        // - Update Product.availableQuantity to remaining after allocation so inventory reflects net available quantity.
        // - Log outcomes and handle errors gracefully.

        if (product == null) {
            logger.warn("Product is null in processing context");
            return product;
        }

        Integer available = product.getAvailableQuantity();
        if (available == null) {
            available = 0;
        }

        // If no inventory, nothing to allocate but ensure value non-negative
        if (available <= 0) {
            product.setAvailableQuantity(0);
            logger.info("Product {} has no available inventory ({}). No reservation allocation performed.", product.getId(), available);
            return product;
        }

        // Build search condition: productId == product.id AND status == ACTIVE
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
            Condition.of("$.productId", "EQUALS", product.getId()),
            Condition.of("$.status", "EQUALS", "ACTIVE")
        );

        try {
            CompletableFuture<List<DataPayload>> future = entityService.getItemsByCondition(
                Reservation.ENTITY_NAME,
                Reservation.ENTITY_VERSION,
                condition,
                true
            );
            List<DataPayload> payloads = future.get();
            List<Reservation> reservations = new ArrayList<>();
            if (payloads != null) {
                for (DataPayload payload : payloads) {
                    try {
                        Reservation r = objectMapper.treeToValue(payload.getData(), Reservation.class);
                        if (r != null) reservations.add(r);
                    } catch (Exception ex) {
                        logger.warn("Failed to deserialize Reservation payload for product {} : {}", product.getId(), ex.getMessage(), ex);
                    }
                }
            }

            if (reservations.isEmpty()) {
                logger.info("No ACTIVE reservations found for product {}", product.getId());
                // nothing else to do
                product.setAvailableQuantity(available);
                return product;
            }

            // Iterate reservations and allocate
            for (Reservation r : reservations) {
                if (r == null) continue;
                Integer reqQty = r.getQty();
                if (reqQty == null || reqQty <= 0) {
                    logger.warn("Skipping reservation {} with invalid qty {}", r.getId(), reqQty);
                    continue;
                }

                if (available >= reqQty) {
                    // Enough inventory to satisfy this reservation -> reduce available and leave reservation ACTIVE
                    available = available - reqQty;
                    logger.info("Reservation {} satisfied for product {} qty {}. Remaining inventory: {}", r.getId(), product.getId(), reqQty, available);
                    // No update to reservation required; it's still ACTIVE and will be consumed downstream
                } else {
                    // Not enough inventory to satisfy this reservation -> expire it
                    String prevStatus = r.getStatus();
                    r.setStatus("EXPIRED");
                    try {
                        CompletableFuture<UUID> updateFuture = entityService.updateItem(UUID.fromString(r.getId()), r);
                        updateFuture.get();
                        logger.info("Reservation {} for product {} set to EXPIRED (was {}).", r.getId(), product.getId(), prevStatus);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Interrupted while updating reservation {}: {}", r.getId(), ie.getMessage(), ie);
                    } catch (ExecutionException ee) {
                        logger.error("Failed to update reservation {}: {}", r.getId(), ee.getMessage(), ee);
                    } catch (Exception e) {
                        logger.error("Unexpected error updating reservation {}: {}", r.getId(), e.getMessage(), e);
                    }
                    // Do not reduce available since none allocated
                }

                // If inventory exhausted, no further allocations possible
                if (available <= 0) {
                    available = 0;
                    break;
                }
            }

            // Update product available quantity to reflect allocations
            product.setAvailableQuantity(available);
            logger.info("Product {} inventory reconciled; updated availableQuantity={}", product.getId(), available);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching reservations for product {}: {}", product.getId(), ie.getMessage(), ie);
        } catch (ExecutionException ee) {
            logger.error("Execution error while fetching reservations for product {}: {}", product.getId(), ee.getMessage(), ee);
        } catch (Exception ex) {
            logger.error("Unexpected error during ProductPublishProcessor for product {}: {}", product.getId(), ex.getMessage(), ex);
        }

        return product;
    }
}