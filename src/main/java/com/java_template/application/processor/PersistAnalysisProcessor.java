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

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@Component
public class PersistAnalysisProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistAnalysisProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PersistAnalysisProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Basic KPI computation for the single sales record
        Integer quantity = entity.getQuantity();
        Double revenue = entity.getRevenue();
        String recordId = entity.getRecordId();
        String dateSold = entity.getDateSold();
        String productId = entity.getProductId();
        String rawSource = entity.getRawSource() != null ? entity.getRawSource() : "";

        // Defensive defaults
        if (quantity == null) quantity = 0;
        if (revenue == null) revenue = 0.0;

        double avgPrice = (quantity > 0) ? (revenue / quantity) : 0.0;

        // Simple business rule to detect underperforming items:
        // - Low volume: quantity < 3
        // - Low average price: avgPrice < 1.0 (fallback threshold)
        String performanceTag = "NORMAL";
        if (quantity < 3 || avgPrice < 1.0) {
            performanceTag = "UNDERPERFORMING";
        }

        // Annotate the SalesRecord rawSource with analysis result (this entity will be persisted automatically)
        String analysisNote = String.format("analysisTag:%s;quantity:%d;revenue:%.2f;avgPrice:%.2f", performanceTag, quantity, revenue, avgPrice);
        entity.setRawSource(rawSource + (rawSource.isBlank() ? "" : " | ") + analysisNote);

        // Persist a lightweight WeeklyReport representing this analysis so downstream workflows/consumers can pick it up.
        WeeklyReport report = new WeeklyReport();
        // reportId uses business-friendly id combining "report-" and the sales record id
        report.setReportId("report-" + (recordId != null ? recordId : UUID.randomUUID().toString()));

        // generatedAt = now
        report.setGeneratedAt(Instant.now().toString());

        // Derive weekStart (ISO date) from dateSold; if parsing fails fallback to today's date
        String weekStart = null;
        if (dateSold != null && !dateSold.isBlank()) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(dateSold);
                weekStart = odt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().toString();
            } catch (DateTimeParseException ex) {
                // Fallback: try to extract date prefix yyyy-MM-dd
                if (dateSold.length() >= 10) {
                    weekStart = dateSold.substring(0, 10);
                } else {
                    weekStart = Instant.now().toString().substring(0, 10);
                }
            }
        } else {
            weekStart = Instant.now().toString().substring(0, 10);
        }
        report.setWeekStart(weekStart);

        // status READY to indicate analysis available
        report.setStatus("READY");

        // summary contains short text with insights
        report.setSummary(String.format("SalesRecord %s for product %s marked as %s. Quantity=%d, Revenue=%.2f, AvgPrice=%.2f",
            recordId, productId, performanceTag, quantity, revenue, avgPrice));

        // attachmentUrl left null (optional)

        // Persist report via EntityService; swallow errors but annotate SalesRecord rawSource on failure
        try {
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                WeeklyReport.ENTITY_NAME,
                WeeklyReport.ENTITY_VERSION,
                report
            );
            UUID reportId = idFuture.get();
            logger.info("Persisted WeeklyReport {} for SalesRecord {}", reportId, recordId);
        } catch (Exception e) {
            logger.error("Failed to persist WeeklyReport for SalesRecord {}: {}", recordId, e.getMessage(), e);
            // annotate the entity rawSource with error info; the entity will be persisted by Cyoda
            entity.setRawSource(entity.getRawSource() + " | analysisPersistError:" + e.getMessage());
        }

        return entity;
    }
}