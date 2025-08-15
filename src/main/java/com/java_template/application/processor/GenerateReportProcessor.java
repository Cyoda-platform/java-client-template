package com.java_template.application.processor;

import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
import com.java_template.application.entity.extractionschedule.version_1.ExtractionSchedule;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class GenerateReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GenerateReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public GenerateReportProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Generating report for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(WeeklyReport.class)
            .validate(this::isValidEntity, "Invalid WeeklyReport state")
            .map(this::processEntityLogic)
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
        WeeklyReport report = context.entity();
        try {
            // Determine period_start and period_end deterministically based on report.report_id or created_on
            // Simple prototype: if period_start/period_end not provided, set to last 7 days
            if (report.getPeriod_start() == null || report.getPeriod_start().isBlank()) {
                ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
                ZonedDateTime start = now.minusDays(7).withHour(0).withMinute(0).withSecond(0).withNano(0);
                ZonedDateTime end = now.withHour(23).withMinute(59).withSecond(59).withNano(0);
                report.setPeriod_start(start.format(DateTimeFormatter.ISO_INSTANT));
                report.setPeriod_end(end.format(DateTimeFormatter.ISO_INSTANT));
            }

            report.setGenerated_on(Instant.now().toString());
            // Build summary metrics and top_products/restock_list heuristically for prototype
            report.setSummary_metrics(new java.util.HashMap<>());
            report.getSummary_metrics().put("sales_volume", 0);
            report.getSummary_metrics().put("revenue", 0);
            report.setTop_products(new ArrayList<>());
            report.setRestock_list(new ArrayList<>());

            // Render report file - in prototype we just set a fake URL
            report.setAttachment_url("https://storage.example.com/reports/" + (report.getReport_id() != null ? report.getReport_id() : UUID.randomUUID().toString()) + ".pdf");
            report.setStatus("READY");

            // Persist report
            CompletableFuture<UUID> idFuture = entityService.addItem(
                WeeklyReport.ENTITY_NAME, String.valueOf(WeeklyReport.ENTITY_VERSION), report
            );
            idFuture.get();
            logger.info("Generated report {} and persisted", report.getReport_id());
        } catch (Exception ex) {
            logger.error("Error generating report {}: {}", report.getReport_id(), ex.getMessage(), ex);
            report.setStatus("FAILED");
        }
        return report;
    }
}
