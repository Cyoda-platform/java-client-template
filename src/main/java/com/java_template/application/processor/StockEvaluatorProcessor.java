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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class StockEvaluatorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StockEvaluatorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public StockEvaluatorProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
            Integer stockLevel = entity.getStockLevel();
            Integer restockThreshold = entity.getRestockThreshold();
            String productRef = entity.getProductId();

            if (stockLevel == null || restockThreshold == null) {
                logger.warn("InventorySnapshot {} has null stockLevel or restockThreshold; skipping evaluation.", entity.getSnapshotId());
                return entity;
            }
            boolean needsRestock = stockLevel < restockThreshold;
            logger.info("Evaluated InventorySnapshot {} for product {}: stockLevel={}, restockThreshold={}, needsRestock={}",
                    entity.getSnapshotId(), productRef, stockLevel, restockThreshold, needsRestock);

            if (productRef == null || productRef.isBlank()) {
                logger.warn("InventorySnapshot {} has no productId; cannot tag product.", entity.getSnapshotId());
                return entity;
            }

            // Try to find associated Product by productId
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.productId", "EQUALS", productRef)
            );

            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    Product.ENTITY_VERSION,
                    condition,
                    true
            );

            List<DataPayload> dataPayloads = itemsFuture.get();
            if (dataPayloads == null || dataPayloads.isEmpty()) {
                logger.info("No Product found for productId {}. Skipping product tagging.", productRef);
                return entity;
            }

            // Update first matching product (business logic: tag product with needsRestock flag in metadata)
            DataPayload payload = dataPayloads.get(0);
            Product product = objectMapper.treeToValue(payload.getData(), Product.class);

            // Prepare metadata JSON node
            ObjectNode metadataNode;
            String existingMetadata = product.getMetadata();
            if (existingMetadata != null && !existingMetadata.isBlank()) {
                try {
                    metadataNode = (ObjectNode) objectMapper.readTree(existingMetadata);
                } catch (Exception ex) {
                    // If metadata is invalid JSON, create a new object node and preserve original string under legacyMetadata
                    metadataNode = objectMapper.createObjectNode();
                    metadataNode.put("legacyMetadata", existingMetadata);
                }
            } else {
                metadataNode = objectMapper.createObjectNode();
            }

            // Set or update needsRestock flag
            metadataNode.put("needsRestock", needsRestock);

            // Write back metadata as compact JSON string
            String newMetadata = objectMapper.writeValueAsString(metadataNode);
            product.setMetadata(newMetadata);

            // Obtain technical id from payload meta to perform update
            String technicalId = null;
            if (payload.getMeta() != null && payload.getMeta().get("entityId") != null) {
                technicalId = payload.getMeta().get("entityId").asText();
            }

            if (technicalId == null || technicalId.isBlank()) {
                logger.warn("Unable to determine technical id for Product {}. Skipping update.", product.getProductId());
                return entity;
            }

            // Update product entity in the store
            try {
                CompletableFuture<UUID> updatedFuture = entityService.updateItem(UUID.fromString(technicalId), product);
                UUID updatedId = updatedFuture.get();
                logger.info("Updated Product {} (techId={}) with needsRestock={}", product.getProductId(), updatedId, needsRestock);
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Failed to update Product {} for restock tagging: {}", product.getProductId(), e.getMessage(), e);
            }

        } catch (InterruptedException | ExecutionException ex) {
            logger.error("Error while evaluating stock for InventorySnapshot {}: {}", entity.getSnapshotId(), ex.getMessage(), ex);
            // swallow and return entity; Cyoda will persist current entity state as needed
        } catch (Exception ex) {
            logger.error("Unexpected error in StockEvaluatorProcessor for snapshot {}: {}", entity.getSnapshotId(), ex.getMessage(), ex);
        }

        return entity;
    }
}