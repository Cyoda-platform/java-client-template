package com.java_template.application.processor;

import com.java_template.application.entity.inventorysnapshot.version_1.InventorySnapshot;
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

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class ReconciliationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReconciliationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReconciliationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InventorySnapshot for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(InventorySnapshot.class)
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

    private boolean isValidEntity(InventorySnapshot entity) {
        return entity != null && entity.isValid();
    }

    private InventorySnapshot processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<InventorySnapshot> context) {
        InventorySnapshot entity = context.entity();
        try {
            logger.info("Reconciling InventorySnapshot: snapshotId={}, productId={}, stockLevel={}, restockThreshold={}",
                    entity.getSnapshotId(), entity.getProductId(), entity.getStockLevel(), entity.getRestockThreshold());

            // Basic sanity checks: ensure numeric fields are present
            if (entity.getStockLevel() == null || entity.getRestockThreshold() == null) {
                logger.warn("InventorySnapshot has null stockLevel or restockThreshold for snapshotId={}", entity.getSnapshotId());
                return entity;
            }

            // If stock is below threshold -> mark product as needing restock by updating Product.metadata
            if (entity.getStockLevel() < entity.getRestockThreshold()) {
                logger.info("Stock below threshold for productId={} ({} < {}), creating restock marker", entity.getProductId(), entity.getStockLevel(), entity.getRestockThreshold());
                try {
                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                            Condition.of("$.productId", "EQUALS", entity.getProductId())
                    );

                    CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                            Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true
                    );
                    List<DataPayload> dataPayloads = itemsFuture.get();

                    if (dataPayloads != null && !dataPayloads.isEmpty()) {
                        // Update first matching product
                        DataPayload payload = dataPayloads.get(0);
                        Product product = objectMapper.treeToValue(payload.getData(), Product.class);
                        String technicalId = payload.getMeta().get("entityId").asText();

                        String existingMetadata = product.getMetadata();
                        if (existingMetadata == null || existingMetadata.isBlank()) {
                            product.setMetadata("{\"restock\":\"NEEDS_RESTOCK\"}");
                        } else if (!existingMetadata.contains("NEEDS_RESTOCK")) {
                            // append a simple marker; do not attempt complex JSON merge to avoid breaking existing format
                            product.setMetadata(existingMetadata + ";restock=NEEDS_RESTOCK");
                        } // if already contains marker, no-op

                        try {
                            CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(technicalId), product);
                            updated.get();
                            logger.info("Marked product {} as NEEDS_RESTOCK (technicalId={})", product.getProductId(), technicalId);
                        } catch (InterruptedException | ExecutionException e) {
                            logger.error("Failed to update Product for restock marker: {}", e.getMessage(), e);
                        }
                    } else {
                        logger.warn("No Product found with productId={} to mark restock", entity.getProductId());
                    }
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to query Product entities for restock handling: {}", e.getMessage(), e);
                }
            } else {
                // Stock is sufficient or replenished -> clear any NEEDS_RESTOCK marker (mark RESTOCKED)
                logger.info("Stock sufficient for productId={} ({} >= {}), ensuring restock marker cleared", entity.getProductId(), entity.getStockLevel(), entity.getRestockThreshold());
                try {
                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                            Condition.of("$.productId", "EQUALS", entity.getProductId())
                    );

                    CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                            Product.ENTITY_NAME, Product.ENTITY_VERSION, condition, true
                    );
                    List<DataPayload> dataPayloads = itemsFuture.get();

                    if (dataPayloads != null && !dataPayloads.isEmpty()) {
                        DataPayload payload = dataPayloads.get(0);
                        Product product = objectMapper.treeToValue(payload.getData(), Product.class);
                        String technicalId = payload.getMeta().get("entityId").asText();

                        String existingMetadata = product.getMetadata();
                        if (existingMetadata != null && existingMetadata.contains("NEEDS_RESTOCK")) {
                            String updatedMetadata = existingMetadata.replace("NEEDS_RESTOCK", "RESTOCKED");
                            product.setMetadata(updatedMetadata);
                            try {
                                CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(technicalId), product);
                                updated.get();
                                logger.info("Updated product {} metadata to RESTOCKED (technicalId={})", product.getProductId(), technicalId);
                            } catch (InterruptedException | ExecutionException e) {
                                logger.error("Failed to update Product to RESTOCKED: {}", e.getMessage(), e);
                            }
                        } else {
                            logger.debug("Product {} has no NEEDS_RESTOCK marker; no update required", product.getProductId());
                        }
                    } else {
                        logger.warn("No Product found with productId={} to clear restock marker", entity.getProductId());
                    }
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to query Product entities for restock-clear handling: {}", e.getMessage(), e);
                }
            }

        } catch (Exception ex) {
            // Catch-all to avoid breaking the processor chain; log detailed error
            logger.error("Unexpected error during reconciliation for snapshotId={}: {}", entity.getSnapshotId(), ex.getMessage(), ex);
        }

        // Return the (possibly modified) InventorySnapshot entity. Cyoda will persist changes automatically.
        return entity;
    }
}