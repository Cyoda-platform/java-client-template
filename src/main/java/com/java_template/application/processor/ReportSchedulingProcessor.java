package com.java_template.application.processor;

import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;

@Component
public class ReportSchedulingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportSchedulingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public ReportSchedulingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report scheduling for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Report.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Report entity) {
        return entity != null && entity.isValid();
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report entity = context.entity();

        logger.info("Scheduling report generation: {}", entity.getReportName());

        // Set report parameters if not already set
        if (entity.getReportName() == null || entity.getReportName().trim().isEmpty()) {
            LocalDate now = LocalDate.now();
            String formattedDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            entity.setReportName("Weekly Performance Report " + formattedDate);
        }

        if (entity.getReportType() == null || entity.getReportType().trim().isEmpty()) {
            entity.setReportType("WEEKLY_SUMMARY");
        }

        // Set report period if not already set
        if (entity.getReportPeriodStart() == null || entity.getReportPeriodEnd() == null) {
            LocalDate now = LocalDate.now();
            LocalDate lastMonday = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            LocalDate lastSunday = lastMonday.plusDays(6);
            
            entity.setReportPeriodStart(lastMonday);
            entity.setReportPeriodEnd(lastSunday);
        }

        if (entity.getGenerationDate() == null) {
            entity.setGenerationDate(LocalDateTime.now());
        }

        // Set file parameters
        if (entity.getFilePath() == null) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            entity.setFilePath("/reports/" + entity.getReportName().replaceAll("\\s+", "_") + "_" + timestamp);
        }

        if (entity.getFileFormat() == null || entity.getFileFormat().trim().isEmpty()) {
            entity.setFileFormat("PDF");
        }

        // Initialize counters and lists
        if (entity.getTotalProducts() == null) {
            entity.setTotalProducts(0);
        }
        if (entity.getTopPerformingProducts() == null) {
            entity.setTopPerformingProducts(new ArrayList<>());
        }
        if (entity.getUnderperformingProducts() == null) {
            entity.setUnderperformingProducts(new ArrayList<>());
        }
        if (entity.getKeyInsights() == null) {
            entity.setKeyInsights(new ArrayList<>());
        }

        logger.info("Report scheduled: {} for period {} to {}", 
                   entity.getReportName(), entity.getReportPeriodStart(), entity.getReportPeriodEnd());
        return entity;
    }
}
