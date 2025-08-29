package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class EnrichProductProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichProductProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EnrichProductProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

    /**
     * Make validation permissive for enrichment stage:
     * Require entity presence and core identifiers (productId and name),
     * allow price to be missing so enrichment can compute/normalize it.
     */
    private boolean isValidEntity(Product entity) {
        if (entity == null) return false;
        if (entity.getProductId() == null || entity.getProductId().isBlank()) return false;
        if (entity.getName() == null || entity.getName().isBlank()) return false;
        // Do not require price here — enrichment will compute or normalize it.
        return true;
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product entity = context.entity();

        // 1. Normalize name (trim)
        try {
            if (entity.getName() != null) {
                entity.setName(entity.getName().trim());
            }
        } catch (Exception ex) {
            logger.warn("Error normalizing product name for productId={}: {}", entity.getProductId(), ex.getMessage());
        }

        // 2. Enrich category: if missing, try to infer from metadata JSON
        try {
            if ((entity.getCategory() == null || entity.getCategory().isBlank()) && entity.getMetadata() != null && !entity.getMetadata().isBlank()) {
                try {
                    JsonNode metaNode = objectMapper.readTree(entity.getMetadata());
                    if (metaNode.has("category") && !metaNode.get("category").asText().isBlank()) {
                        entity.setCategory(metaNode.get("category").asText());
                    } else if (metaNode.has("type") && !metaNode.get("type").asText().isBlank()) {
                        entity.setCategory(metaNode.get("type").asText());
                    }
                } catch (Exception parseEx) {
                    logger.debug("Failed to parse metadata for productId={}: {}", entity.getProductId(), parseEx.getMessage());
                }
            }
            if (entity.getCategory() == null || entity.getCategory().isBlank()) {
                entity.setCategory("Uncategorized");
            }
        } catch (Exception ex) {
            logger.warn("Error enriching category for productId={}: {}", entity.getProductId(), ex.getMessage());
            if (entity.getCategory() == null) entity.setCategory("Uncategorized");
        }

        // 3. Price normalization/enrichment:
        //    - If price missing or invalid, attempt to compute average price from SalesRecord entries
        //    - Round price to 2 decimals and ensure non-negative
        try {
            Double price = entity.getPrice();
            boolean needEstimate = (price == null || price < 0.0);

            if (needEstimate && entity.getProductId() != null && !entity.getProductId().isBlank()) {
                try {
                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.productId", "EQUALS", entity.getProductId())
                    );
                    CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                        SalesRecord.ENTITY_NAME,
                        SalesRecord.ENTITY_VERSION,
                        condition,
                        true
                    );
                    List<DataPayload> dataPayloads = filteredItemsFuture.get();
                    List<SalesRecord> sales = new ArrayList<>();
                    if (dataPayloads != null) {
                        for (DataPayload payload : dataPayloads) {
                            try {
                                SalesRecord sr = objectMapper.treeToValue(payload.getData(), SalesRecord.class);
                                if (sr != null) sales.add(sr);
                            } catch (Exception convEx) {
                                logger.debug("Failed to convert DataPayload to SalesRecord for productId={}: {}", entity.getProductId(), convEx.getMessage());
                            }
                        }
                    }
                    double totalRevenue = 0.0;
                    int totalQuantity = 0;
                    for (SalesRecord sr : sales) {
                        if (sr.getQuantity() != null && sr.getRevenue() != null) {
                            totalQuantity += sr.getQuantity();
                            totalRevenue += sr.getRevenue();
                        }
                    }
                    if (totalQuantity > 0) {
                        double avgPrice = totalRevenue / totalQuantity;
                        BigDecimal bd = BigDecimal.valueOf(avgPrice).setScale(2, RoundingMode.HALF_UP);
                        entity.setPrice(bd.doubleValue());
                    }
                } catch (Exception e) {
                    logger.debug("Failed to compute price from SalesRecord for productId={}: {}", entity.getProductId(), e.getMessage());
                }
            }

            // Final normalization: ensure price is non-null and non-negative
            if (entity.getPrice() == null) {
                entity.setPrice(0.0);
            } else {
                if (entity.getPrice() < 0.0) {
                    entity.setPrice(0.0);
                } else {
                    BigDecimal bd = BigDecimal.valueOf(entity.getPrice()).setScale(2, RoundingMode.HALF_UP);
                    entity.setPrice(bd.doubleValue());
                }
            }
        } catch (Exception ex) {
            logger.warn("Error enriching price for productId={}: {}", entity.getProductId(), ex.getMessage());
            if (entity.getPrice() == null) entity.setPrice(0.0);
        }

        // All enrichment done; the workflow will persist the updated entity state.
        return entity;
    }
}