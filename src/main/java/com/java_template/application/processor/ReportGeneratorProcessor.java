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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ReportGeneratorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportGeneratorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReportGeneratorProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        try {
            // Mark as generating to indicate processing started
            entity.setStatus("GENERATING");
            // Ensure generatedAt set to current time if not set
            String nowIso = Instant.now().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString();
            entity.setGeneratedAt(nowIso);

            String weekStartStr = entity.getWeekStart();
            if (weekStartStr == null || weekStartStr.isBlank()) {
                logger.warn("Week start is missing on report {}, marking as FAILED", entity.getReportId());
                entity.setStatus("FAILED");
                entity.setSummary("Week start missing for report generation.");
                return entity;
            }

            LocalDate weekStart;
            try {
                weekStart = LocalDate.parse(weekStartStr);
            } catch (DateTimeParseException ex) {
                logger.error("Invalid weekStart format for report {}: {}", entity.getReportId(), weekStartStr, ex);
                entity.setStatus("FAILED");
                entity.setSummary("Invalid weekStart format: " + weekStartStr);
                return entity;
            }
            LocalDate weekEnd = weekStart.plusDays(6);

            // Load SalesRecords
            CompletableFuture<List<DataPayload>> salesFuture = entityService.getItems(
                SalesRecord.ENTITY_NAME,
                SalesRecord.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> salesPayloads = salesFuture.get();
            List<SalesRecord> sales = new ArrayList<>();
            if (salesPayloads != null) {
                for (DataPayload payload : salesPayloads) {
                    try {
                        SalesRecord sr = objectMapper.treeToValue(payload.getData(), SalesRecord.class);
                        if (sr != null && sr.getDateSold() != null) {
                            // Filter by week range (inclusive)
                            try {
                                ZonedDateTime dt = ZonedDateTime.parse(sr.getDateSold());
                                LocalDate soldDate = dt.toLocalDate();
                                if ((soldDate.isEqual(weekStart) || soldDate.isAfter(weekStart)) &&
                                    (soldDate.isEqual(weekEnd) || soldDate.isBefore(weekEnd))) {
                                    sales.add(sr);
                                }
                            } catch (DateTimeParseException e) {
                                // try parsing as LocalDateTime or LocalDate
                                try {
                                    LocalDateTime ldt = LocalDateTime.parse(sr.getDateSold());
                                    LocalDate soldDate = ldt.toLocalDate();
                                    if ((soldDate.isEqual(weekStart) || soldDate.isAfter(weekStart)) &&
                                        (soldDate.isEqual(weekEnd) || soldDate.isBefore(weekEnd))) {
                                        sales.add(sr);
                                    }
                                } catch (Exception ex) {
                                    logger.debug("Unable to parse dateSold {} for record {}, skipping", sr.getDateSold(), sr.getRecordId());
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to convert sales payload to SalesRecord: {}", e.getMessage());
                    }
                }
            }

            // Aggregate revenue and quantity per productId
            Map<String, Double> revenueByProduct = new HashMap<>();
            Map<String, Integer> qtyByProduct = new HashMap<>();
            for (SalesRecord sr : sales) {
                String pid = sr.getProductId();
                if (pid == null) continue;
                revenueByProduct.put(pid, revenueByProduct.getOrDefault(pid, 0.0) + (sr.getRevenue() != null ? sr.getRevenue() : 0.0));
                qtyByProduct.put(pid, qtyByProduct.getOrDefault(pid, 0) + (sr.getQuantity() != null ? sr.getQuantity() : 0));
            }

            // Load Products to map names/categories
            CompletableFuture<List<DataPayload>> productsFuture = entityService.getItems(
                Product.ENTITY_NAME,
                Product.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> productPayloads = productsFuture.get();
            Map<String, Product> productMap = new HashMap<>();
            if (productPayloads != null) {
                for (DataPayload payload : productPayloads) {
                    try {
                        Product p = objectMapper.treeToValue(payload.getData(), Product.class);
                        if (p != null && p.getProductId() != null) {
                            productMap.put(p.getProductId(), p);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to convert product payload: {}", e.getMessage());
                    }
                }
            }

            // Load InventorySnapshots and determine restock needs (use latest snapshot per product)
            CompletableFuture<List<DataPayload>> inventoryFuture = entityService.getItems(
                InventorySnapshot.ENTITY_NAME,
                InventorySnapshot.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> inventoryPayloads = inventoryFuture.get();
            Map<String, InventorySnapshot> latestSnapshotByProduct = new HashMap<>();
            if (inventoryPayloads != null) {
                for (DataPayload payload : inventoryPayloads) {
                    try {
                        InventorySnapshot snap = objectMapper.treeToValue(payload.getData(), InventorySnapshot.class);
                        if (snap == null || snap.getProductId() == null) continue;
                        InventorySnapshot existing = latestSnapshotByProduct.get(snap.getProductId());
                        if (existing == null) {
                            latestSnapshotByProduct.put(snap.getProductId(), snap);
                        } else {
                            // choose snapshot with later snapshotAt
                            try {
                                Instant cur = Instant.parse(snap.getSnapshotAt());
                                Instant prev = Instant.parse(existing.getSnapshotAt());
                                if (cur.isAfter(prev)) {
                                    latestSnapshotByProduct.put(snap.getProductId(), snap);
                                }
                            } catch (Exception ex) {
                                // fallback: keep existing
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to convert inventory payload: {}", e.getMessage());
                    }
                }
            }

            // Determine top seller
            String topProductId = null;
            double topRevenue = 0.0;
            for (Map.Entry<String, Double> entry : revenueByProduct.entrySet()) {
                if (entry.getValue() > topRevenue || topProductId == null) {
                    topProductId = entry.getKey();
                    topRevenue = entry.getValue();
                }
            }

            // Determine products needing restock
            List<String> needsRestock = new ArrayList<>();
            for (Map.Entry<String, InventorySnapshot> entry : latestSnapshotByProduct.entrySet()) {
                InventorySnapshot snap = entry.getValue();
                if (snap.getStockLevel() != null && snap.getRestockThreshold() != null) {
                    if (snap.getStockLevel() <= snap.getRestockThreshold()) {
                        String pid = entry.getKey();
                        Product product = productMap.get(pid);
                        String nameOrId = (product != null && product.getName() != null) ? product.getName() : pid;
                        needsRestock.add(nameOrId);
                    }
                }
            }

            // Compose summary
            StringBuilder summary = new StringBuilder();
            if (sales.isEmpty()) {
                summary.append("No sales data found for week starting ").append(weekStartStr).append(".");
            } else {
                if (topProductId != null) {
                    Product topProduct = productMap.get(topProductId);
                    String topName = topProduct != null ? topProduct.getName() : topProductId;
                    int topQty = qtyByProduct.getOrDefault(topProductId, 0);
                    summary.append("Top seller: ").append(topName)
                        .append(" (revenue: ").append(String.format("%.2f", topRevenue))
                        .append(", qty: ").append(topQty).append("). ");
                } else {
                    summary.append("No top seller identified. ");
                }
            }
            if (!needsRestock.isEmpty()) {
                summary.append("Products needing restock: ").append(String.join(", ", needsRestock)).append(".");
            } else {
                summary.append("No products require restocking.");
            }

            entity.setSummary(summary.toString());
            // After generating the summary, mark as ready for next steps (template application / export)
            entity.setStatus("GENERATING"); // keep GENERATING while downstream processors act; alternatively could be "READY_FOR_TEMPLATE"
            // Leave attachmentUrl to downstream processors (ExportToPDFProcessor) to fill

            logger.info("WeeklyReport {} generated summary: {}", entity.getReportId(), entity.getSummary());

            return entity;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while generating report {}: {}", entity.getReportId(), ie.getMessage(), ie);
            entity.setStatus("FAILED");
            entity.setSummary("Report generation interrupted: " + ie.getMessage());
            return entity;
        } catch (ExecutionException ee) {
            logger.error("Execution error while generating report {}: {}", entity.getReportId(), ee.getMessage(), ee);
            entity.setStatus("FAILED");
            entity.setSummary("Report generation failed: " + ee.getMessage());
            return entity;
        } catch (Exception ex) {
            logger.error("Unexpected error during report generation {}: {}", entity.getReportId(), ex.getMessage(), ex);
            entity.setStatus("FAILED");
            entity.setSummary("Unexpected error: " + ex.getMessage());
            return entity;
        }
    }
}