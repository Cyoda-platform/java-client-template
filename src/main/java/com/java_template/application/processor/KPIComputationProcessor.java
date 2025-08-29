package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.inventorysnapshot.version_1.InventorySnapshot;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
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

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@Component
public class KPIComputationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(KPIComputationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // Business thresholds
    private static final int MIN_SALES_QTY = 5;
    private static final double MIN_TURNOVER = 0.1d;

    public KPIComputationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SalesRecord for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(SalesRecord.class)
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

    private boolean isValidEntity(SalesRecord entity) {
        return entity != null && entity.isValid();
    }

    private SalesRecord processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<SalesRecord> context) {
        SalesRecord entity = context.entity();
        try {
            // Compute simple KPIs from SalesRecord
            int qty = entity.getQuantity() != null ? entity.getQuantity() : 0;
            double revenue = entity.getRevenue() != null ? entity.getRevenue() : 0.0;
            double unitPrice = qty > 0 ? (revenue / qty) : 0.0;

            // Attempt to load latest inventory snapshot for the product to compute turnover
            Double turnover = null;
            try {
                SearchConditionRequest invCondition = SearchConditionRequest.group(
                    "AND",
                    Condition.of("$.productId", "EQUALS", entity.getProductId())
                );
                CompletableFuture<List<DataPayload>> invFuture = entityService.getItemsByCondition(
                    InventorySnapshot.ENTITY_NAME,
                    InventorySnapshot.ENTITY_VERSION,
                    invCondition,
                    true
                );
                List<DataPayload> invPayloads = invFuture.get();
                if (invPayloads != null && !invPayloads.isEmpty()) {
                    // pick latest snapshot by snapshotAt (ISO-8601 lexicographic comparable)
                    DataPayload latest = invPayloads.stream()
                        .max(Comparator.comparing(p -> {
                            JsonNode node = p.getData();
                            JsonNode sa = node.get("snapshotAt");
                            return sa != null ? sa.asText() : "";
                        }))
                        .orElse(null);
                    if (latest != null) {
                        InventorySnapshot snap = objectMapper.treeToValue(latest.getData(), InventorySnapshot.class);
                        Integer stock = snap.getStockLevel() != null ? snap.getStockLevel() : 0;
                        int denom = stock <= 0 ? 1 : stock;
                        turnover = denom > 0 ? ((double) qty) / denom : null;
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to read InventorySnapshot for product {}: {}", entity.getProductId(), e.getMessage());
            }

            // Determine performance tag
            String performanceTag = "NORMAL";
            if (qty < MIN_SALES_QTY) {
                performanceTag = "UNDERPERFORMING";
            } else if (turnover != null && turnover < MIN_TURNOVER) {
                performanceTag = "UNDERPERFORMING";
            }

            // Update Product metadata to persist KPIs and tag (do not modify SalesRecord itself)
            try {
                SearchConditionRequest prodCondition = SearchConditionRequest.group(
                    "AND",
                    Condition.of("$.productId", "EQUALS", entity.getProductId())
                );
                CompletableFuture<List<DataPayload>> prodFuture = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    Product.ENTITY_VERSION,
                    prodCondition,
                    true
                );
                List<DataPayload> prodPayloads = prodFuture.get();
                if (prodPayloads != null && !prodPayloads.isEmpty()) {
                    // Update all matching products (typically one)
                    for (DataPayload payload : prodPayloads) {
                        Product product = objectMapper.treeToValue(payload.getData(), Product.class);
                        String technicalId = payload.getMeta().get("entityId").asText();

                        ObjectNode metadataNode;
                        try {
                            if (product.getMetadata() != null && !product.getMetadata().isBlank()) {
                                JsonNode parsed = objectMapper.readTree(product.getMetadata());
                                if (parsed != null && parsed.isObject()) {
                                    metadataNode = (ObjectNode) parsed;
                                } else {
                                    metadataNode = objectMapper.createObjectNode();
                                }
                            } else {
                                metadataNode = objectMapper.createObjectNode();
                            }
                        } catch (Exception ex) {
                            metadataNode = objectMapper.createObjectNode();
                        }

                        // Add/update KPI fields
                        metadataNode.put("lastSaleAt", entity.getDateSold());
                        metadataNode.put("lastSaleQuantity", qty);
                        metadataNode.put("lastSaleRevenue", revenue);
                        metadataNode.put("lastSaleUnitPrice", unitPrice);
                        if (turnover != null) {
                            metadataNode.put("lastComputedTurnover", turnover);
                        }
                        metadataNode.put("performanceTag", performanceTag);

                        product.setMetadata(objectMapper.writeValueAsString(metadataNode));

                        // Persist update
                        try {
                            entityService.updateItem(UUID.fromString(technicalId), product).get();
                            logger.info("Updated Product {} metadata with KPI info and tag={}", product.getProductId(), performanceTag);
                        } catch (Exception updEx) {
                            logger.error("Failed to update Product {}: {}", product.getProductId(), updEx.getMessage(), updEx);
                        }
                    }
                } else {
                    logger.debug("No Product entity found for productId={}, skipping product update", entity.getProductId());
                }
            } catch (Exception e) {
                logger.error("Failed to process Product updates for productId {}: {}", entity.getProductId(), e.getMessage(), e);
            }

        } catch (Exception e) {
            logger.error("Error computing KPIs for SalesRecord {}: {}", entity.getRecordId(), e.getMessage(), e);
        }

        // Return the original SalesRecord (the platform will persist it if required)
        return entity;
    }
}