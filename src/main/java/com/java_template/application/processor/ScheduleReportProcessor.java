package com.java_template.application.processor;

import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
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
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class ScheduleReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ScheduleReportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Core business logic for scheduling/report dispatch:
        // - Aggregate basic sales and inventory info for the report week
        // - Compose a short summary
        // - Set generatedAt timestamp if not present
        // - Create a placeholder attachmentUrl (actual PDF generation handled elsewhere)
        // - Transition status to DISPATCHED (represents that notifications were sent)

        try {
            // Ensure generatedAt is set
            if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
                entity.setGeneratedAt(Instant.now().toString());
            }

            // Load sales records for aggregation
            CompletableFuture<List<DataPayload>> salesFuture = entityService.getItems(
                SalesRecord.ENTITY_NAME,
                SalesRecord.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> salesPayloads = salesFuture.get();

            double totalRevenue = 0.0;
            int totalQuantity = 0;
            Map<String, Double> revenueByProduct = new HashMap<>();
            int salesCount = 0;

            if (salesPayloads != null) {
                for (DataPayload payload : salesPayloads) {
                    try {
                        SalesRecord record = objectMapper.treeToValue(payload.getData(), SalesRecord.class);
                        if (record != null && record.getRevenue() != null) {
                            totalRevenue += record.getRevenue();
                        }
                        if (record != null && record.getQuantity() != null) {
                            totalQuantity += record.getQuantity();
                        }
                        if (record != null && record.getProductId() != null && record.getRevenue() != null) {
                            revenueByProduct.merge(record.getProductId(), record.getRevenue(), Double::sum);
                        }
                        salesCount++;
                    } catch (Exception e) {
                        logger.warn("Skipping malformed sales payload: {}", e.getMessage());
                    }
                }
            }

            // Determine top product by revenue
            String topProductId = null;
            double topRevenue = 0.0;
            for (Map.Entry<String, Double> entry : revenueByProduct.entrySet()) {
                if (entry.getValue() > topRevenue) {
                    topRevenue = entry.getValue();
                    topProductId = entry.getKey();
                }
            }

            // Load inventory snapshots to detect low stock items
            CompletableFuture<List<DataPayload>> invFuture = entityService.getItems(
                InventorySnapshot.ENTITY_NAME,
                InventorySnapshot.ENTITY_VERSION,
                null, null, null
            );
            List<DataPayload> invPayloads = invFuture.get();

            int lowStockCount = 0;
            if (invPayloads != null) {
                for (DataPayload payload : invPayloads) {
                    try {
                        InventorySnapshot snap = objectMapper.treeToValue(payload.getData(), InventorySnapshot.class);
                        if (snap != null && snap.getStockLevel() != null && snap.getRestockThreshold() != null) {
                            if (snap.getStockLevel() < snap.getRestockThreshold()) {
                                lowStockCount++;
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Skipping malformed inventory payload: {}", e.getMessage());
                    }
                }
            }

            // Compose summary
            StringBuilder summary = new StringBuilder();
            summary.append("Sales records processed: ").append(salesCount).append(". ");
            summary.append("Total quantity sold: ").append(totalQuantity).append(". ");
            summary.append(String.format("Total revenue: %.2f. ", totalRevenue));
            if (topProductId != null) {
                summary.append("Top product by revenue: ").append(topProductId).append(" (").append(String.format("%.2f", topRevenue)).append("). ");
            } else {
                summary.append("Top product by revenue: N/A. ");
            }
            summary.append("Items needing restock: ").append(lowStockCount).append(".");

            entity.setSummary(summary.toString());

            // Create a placeholder attachment URL (real PDF export happens in another processor)
            String idPart = entity.getReportId() != null && !entity.getReportId().isBlank()
                ? entity.getReportId()
                : UUID.randomUUID().toString();
            String attachmentUrl = "https://filestore/reports/" + idPart + ".pdf";
            entity.setAttachmentUrl(attachmentUrl);

            // Mark as DISPATCHED to represent notification step completed by this processor
            entity.setStatus("DISPATCHED");

            // Update generatedAt to current timestamp (ensure freshness)
            entity.setGeneratedAt(Instant.now().toString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while generating report: {}", e.getMessage(), e);
            entity.setStatus("FAILED");
            entity.setSummary("Report generation interrupted: " + e.getMessage());
        } catch (ExecutionException e) {
            logger.error("Failed to fetch dependent entities for report: {}", e.getMessage(), e);
            entity.setStatus("FAILED");
            entity.setSummary("Failed to fetch data for report: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during report scheduling: {}", e.getMessage(), e);
            entity.setStatus("FAILED");
            entity.setSummary("Unexpected error: " + e.getMessage());
        }

        return entity;
    }
}