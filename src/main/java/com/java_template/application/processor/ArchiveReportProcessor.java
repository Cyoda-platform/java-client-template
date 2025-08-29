package com.java_template.application.processor;

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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.Duration;

@Component
public class ArchiveReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ArchiveReportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Business rule:
        // Archive weekly reports after a retention window has passed.
        // If the report status indicates it was dispatched (delivered), and the generatedAt timestamp
        // is older than the retention window, set status = "ARCHIVED".
        //
        // Retention window is set to 30 days by default.

        final int retentionDays = 30;
        String status = entity.getStatus();
        String generatedAtStr = entity.getGeneratedAt();

        if (status == null || generatedAtStr == null) {
            logger.warn("WeeklyReport missing status or generatedAt, skipping archive. reportId={}", entity.getReportId());
            return entity;
        }

        // Only archive reports that have been dispatched/completed
        if (!status.equalsIgnoreCase("DISPATCHED") && !status.equalsIgnoreCase("READY")) {
            logger.info("WeeklyReport status is '{}'; not eligible for archive now. reportId={}", status, entity.getReportId());
            return entity;
        }

        try {
            OffsetDateTime generatedAt;
            try {
                generatedAt = OffsetDateTime.parse(generatedAtStr);
            } catch (DateTimeParseException ex) {
                // Fallback: try parsing as LocalDate (weekStart-like formats)
                LocalDate localDate = LocalDate.parse(generatedAtStr);
                generatedAt = localDate.atStartOfDay().atOffset(ZoneOffset.UTC);
            }

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            Duration duration = Duration.between(generatedAt, now);
            long daysElapsed = duration.toDays();

            if (daysElapsed >= retentionDays) {
                logger.info("Archiving WeeklyReport reportId={} generatedAt={} daysElapsed={}", entity.getReportId(), generatedAtStr, daysElapsed);
                entity.setStatus("ARCHIVED");
                // No additional entityService updates for the triggering entity are performed;
                // the workflow persistence will persist this updated entity state automatically.
            } else {
                logger.info("WeeklyReport not old enough to archive. reportId={} daysElapsed={}", entity.getReportId(), daysElapsed);
            }
        } catch (Exception e) {
            logger.error("Failed to evaluate archive condition for WeeklyReport reportId={}: {}", entity.getReportId(), e.getMessage(), e);
            // Do not modify entity on parse/processing errors.
        }

        return entity;
    }
}