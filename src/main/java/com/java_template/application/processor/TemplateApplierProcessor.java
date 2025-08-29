package com.java_template.application.processor;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.inventorysnapshot.version_1.InventorySnapshot;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.*;
import static com.java_template.common.config.Config.*;

@Component
public class TemplateApplierProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TemplateApplierProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public TemplateApplierProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklyReport for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(WeeklyReport.class)
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

    private boolean isValidEntity(WeeklyReport entity) {
        return entity != null && entity.isValid();
    }

    private WeeklyReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklyReport> context) {
        WeeklyReport entity = context.entity();

        // Only apply template when report is in a generating/created state.
        if (entity == null) {
            logger.warn("Received null WeeklyReport in TemplateApplierProcessor");
            return entity;
        }

        String status = entity.getStatus();
        if (status == null) status = "";

        // If report is not in a generating-like state, skip template application.
        if (!(status.equalsIgnoreCase("GENERATING") || status.equalsIgnoreCase("CREATING") || status.equalsIgnoreCase("CREATED"))) {
            logger.info("WeeklyReport {} has status '{}', skipping template application", entity.getReportId(), status);
            return entity;
        }

        try {
            // Fetch SalesRecords
            CompletableFuture<List<DataPayload>> salesFuture = entityService.getItems(
                SalesRecord.ENTITY_NAME,
                SalesRecord.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> salesPayloads = salesFuture.get();

            // Aggregate sales by productId
            Map<String, Double> revenueByProduct = new HashMap<>();
            Map<String, Integer> qtyByProduct = new HashMap<>();
            if (salesPayloads != null) {
                for (DataPayload payload : salesPayloads) {
                    try {
                        SalesRecord sr = objectMapper.treeToValue(payload.getData(), SalesRecord.class);
                        if (sr != null && sr.getProductId() != null) {
                            revenueByProduct.merge(sr.getProductId(), sr.getRevenue() == null ? 0.0 : sr.getRevenue(), Double::sum);
                            qtyByProduct.merge(sr.getProductId(), sr.getQuantity() == null ? 0 : sr.getQuantity(), Integer::sum);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse SalesRecord payload while applying template: {}", e.getMessage());
                    }
                }
            }

            // Fetch Products to resolve names
            CompletableFuture<List<DataPayload>> productsFuture = entityService.getItems(
                Product.ENTITY_NAME,
                Product.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> productPayloads = productsFuture.get();
            Map<String, Product> productById = new HashMap<>();
            if (productPayloads != null) {
                for (DataPayload payload : productPayloads) {
                    try {
                        Product p = objectMapper.treeToValue(payload.getData(), Product.class);
                        if (p != null && p.getProductId() != null) {
                            productById.put(p.getProductId(), p);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse Product payload while applying template: {}", e.getMessage());
                    }
                }
            }

            // Determine top seller by revenue, fallback to quantity
            String topProductId = null;
            double topRevenue = -1.0;
            for (Map.Entry<String, Double> entry : revenueByProduct.entrySet()) {
                if (entry.getValue() > topRevenue) {
                    topRevenue = entry.getValue();
                    topProductId = entry.getKey();
                }
            }
            if (topProductId == null && !qtyByProduct.isEmpty()) {
                int topQty = -1;
                for (Map.Entry<String, Integer> entry : qtyByProduct.entrySet()) {
                    if (entry.getValue() > topQty) {
                        topQty = entry.getValue();
                        topProductId = entry.getKey();
                    }
                }
            }

            String topSellerDisplay = "N/A";
            if (topProductId != null) {
                Product p = productById.get(topProductId);
                if (p != null && p.getName() != null && !p.getName().isBlank()) {
                    topSellerDisplay = p.getName();
                } else {
                    topSellerDisplay = topProductId;
                }
            }

            // Fetch InventorySnapshots to determine restock needs
            CompletableFuture<List<DataPayload>> inventoryFuture = entityService.getItems(
                InventorySnapshot.ENTITY_NAME,
                InventorySnapshot.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> inventoryPayloads = inventoryFuture.get();
            Set<String> needsRestock = new HashSet<>();
            if (inventoryPayloads != null) {
                for (DataPayload payload : inventoryPayloads) {
                    try {
                        InventorySnapshot snap = objectMapper.treeToValue(payload.getData(), InventorySnapshot.class);
                        if (snap != null && snap.getProductId() != null && snap.getStockLevel() != null && snap.getRestockThreshold() != null) {
                            if (snap.getStockLevel() < snap.getRestockThreshold()) {
                                needsRestock.add(snap.getProductId());
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse InventorySnapshot payload while applying template: {}", e.getMessage());
                    }
                }
            }

            int restockCount = needsRestock.size();

            // Map restock product names if available
            List<String> restockNames = new ArrayList<>();
            for (String prodId : needsRestock) {
                Product p = productById.get(prodId);
                if (p != null && p.getName() != null && !p.getName().isBlank()) restockNames.add(p.getName());
                else restockNames.add(prodId);
            }

            // Build summary using available data
            StringBuilder summaryBuilder = new StringBuilder();
            summaryBuilder.append("Top seller: ").append(topSellerDisplay);
            if (topRevenue >= 0) {
                summaryBuilder.append(" (revenue: ").append(String.format("%.2f", topRevenue)).append(")");
            }
            summaryBuilder.append("; ");
            summaryBuilder.append(restockCount).append(" SKUs need restocking");
            if (!restockNames.isEmpty()) {
                summaryBuilder.append(" (").append(String.join(", ", restockNames)).append(")");
            }

            // Apply template result to the entity
            if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
                entity.setGeneratedAt(Instant.now().toString());
            }
            entity.setSummary(summaryBuilder.toString());
            // Attachment URL is a placeholder; actual ExportToPDFProcessor will replace/upload the real PDF.
            if (entity.getAttachmentUrl() == null || entity.getAttachmentUrl().isBlank()) {
                String safeReportId = entity.getReportId() != null ? entity.getReportId() : UUID.randomUUID().toString();
                entity.setAttachmentUrl("https://filestore/reports/" + safeReportId + ".pdf");
            }
            // Move status forward in workflow to indicate template applied
            entity.setStatus("TEMPLATE_APPLIED");

            logger.info("Applied template to WeeklyReport {}: topSeller={}, restockCount={}", entity.getReportId(), topSellerDisplay, restockCount);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while applying template for WeeklyReport {}: {}", entity.getReportId(), ie.getMessage(), ie);
            entity.setStatus("FAILED");
            if (entity.getSummary() == null || entity.getSummary().isBlank()) {
                entity.setSummary("Template application interrupted.");
            }
        } catch (ExecutionException ee) {
            logger.error("Execution error while applying template for WeeklyReport {}: {}", entity.getReportId(), ee.getMessage(), ee);
            entity.setStatus("FAILED");
            if (entity.getSummary() == null || entity.getSummary().isBlank()) {
                entity.setSummary("Template application failed: " + ee.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while applying template for WeeklyReport {}: {}", entity.getReportId(), e.getMessage(), e);
            entity.setStatus("FAILED");
            if (entity.getSummary() == null || entity.getSummary().isBlank()) {
                entity.setSummary("Template application failed: " + e.getMessage());
            }
        }

        return entity;
    }
}