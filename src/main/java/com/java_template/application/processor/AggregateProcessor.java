package com.java_template.application.processor;

import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

@Component
public class AggregateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AggregateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AggregateProcessor(SerializerFactory serializerFactory,
                              EntityService entityService,
                              ObjectMapper objectMapper) {
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

        // Business logic: aggregate sales records by product and week, then persist a WeeklyReport summary.
        try {
            String productId = entity.getProductId();
            String dateSold = entity.getDateSold(); // ISO-8601 timestamp

            if (productId == null || productId.isBlank() || dateSold == null || dateSold.isBlank()) {
                logger.warn("SalesRecord missing productId or dateSold, skipping aggregation for recordId={}", entity.getRecordId());
                return entity;
            }

            // Parse dateSold to compute week start (Monday)
            Instant soldInstant;
            try {
                soldInstant = Instant.parse(dateSold);
            } catch (Exception ex) {
                // Fallback: if parsing fails, skip aggregation for this record
                logger.warn("Failed to parse dateSold='{}' for recordId={}: {}", dateSold, entity.getRecordId(), ex.getMessage());
                return entity;
            }
            LocalDate soldDate = soldInstant.atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate weekStartDate = soldDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate weekEndExclusive = weekStartDate.plusDays(7);

            String weekStartStr = weekStartDate.toString(); // ISO date string e.g., 2025-08-18

            // Fetch all SalesRecord items and filter in-memory for same product and same week
            List<DataPayload> dataPayloads = null;
            try {
                CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    SalesRecord.ENTITY_NAME,
                    SalesRecord.ENTITY_VERSION,
                    null, null, null
                );
                dataPayloads = itemsFuture.get();
            } catch (Exception ex) {
                logger.error("Failed to retrieve SalesRecord items for aggregation: {}", ex.getMessage(), ex);
                return entity;
            }

            int totalQuantity = 0;
            double totalRevenue = 0.0;
            int recordsCount = 0;

            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    try {
                        SalesRecord sr = objectMapper.treeToValue(payload.getData(), SalesRecord.class);
                        if (sr == null) continue;
                        if (!productId.equals(sr.getProductId())) continue;
                        String srDateSold = sr.getDateSold();
                        if (srDateSold == null || srDateSold.isBlank()) continue;
                        Instant srInstant;
                        try {
                            srInstant = Instant.parse(srDateSold);
                        } catch (Exception pe) {
                            continue;
                        }
                        LocalDate srLocalDate = srInstant.atZone(ZoneOffset.UTC).toLocalDate();
                        // check if srLocalDate in [weekStartDate, weekEndExclusive)
                        if ((srLocalDate.isEqual(weekStartDate) || srLocalDate.isAfter(weekStartDate)) &&
                            srLocalDate.isBefore(weekEndExclusive)) {
                            Integer q = sr.getQuantity();
                            Double r = sr.getRevenue();
                            if (q != null) totalQuantity += q;
                            if (r != null) totalRevenue += r;
                            recordsCount++;
                        }
                    } catch (Exception ex) {
                        logger.debug("Skipping payload during aggregation due to conversion error: {}", ex.getMessage());
                    }
                }
            }

            double avgPrice = totalQuantity > 0 ? (totalRevenue / totalQuantity) : 0.0;

            // Create a WeeklyReport summarizing aggregated results for this product-week
            WeeklyReport report = new WeeklyReport();
            String reportId = "weekly-agg-" + productId + "-" + weekStartStr;
            report.setReportId(reportId);
            report.setWeekStart(weekStartStr);
            report.setGeneratedAt(Instant.now().toString());
            report.setStatus("READY");
            StringBuilder summaryBuilder = new StringBuilder();
            summaryBuilder.append("Aggregation for productId=").append(productId)
                    .append(" weekStart=").append(weekStartStr)
                    .append(" totalRecords=").append(recordsCount)
                    .append(" totalQuantity=").append(totalQuantity)
                    .append(" totalRevenue=").append(String.format("%.2f", totalRevenue))
                    .append(" avgPrice=").append(String.format("%.2f", avgPrice));
            report.setSummary(summaryBuilder.toString());

            // Persist the WeeklyReport
            try {
                CompletableFuture<java.util.UUID> added = entityService.addItem(
                    WeeklyReport.ENTITY_NAME,
                    WeeklyReport.ENTITY_VERSION,
                    report
                );
                java.util.UUID addedId = added.get();
                logger.info("Persisted WeeklyReport {} for product={} weekStart={} as id={}", reportId, productId, weekStartStr, addedId);
            } catch (Exception ex) {
                logger.error("Failed to persist WeeklyReport for product={} weekStart={}: {}", productId, weekStartStr, ex.getMessage(), ex);
            }

        } catch (Exception ex) {
            logger.error("Unexpected error during aggregation processing for recordId={}: {}", entity.getRecordId(), ex.getMessage(), ex);
        }

        // Return the original SalesRecord (the workflow persistence will handle saving if needed)
        return entity;
    }
}