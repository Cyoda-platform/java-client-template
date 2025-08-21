package com.java_template.application.processor;

import com.java_template.application.entity.monthlyreport.version_1.MonthlyReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ArchiveReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ArchiveReportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ArchiveReportProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(MonthlyReport.class)
            .validate(this::isValidEntity, "Invalid monthly report for archive")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(MonthlyReport report) {
        return report != null && report.isValid();
    }

    private MonthlyReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<MonthlyReport> context) {
        MonthlyReport report = context.entity();
        try {
            report.setStatus("ARCHIVED");
            report.setGeneratedAt(report.getGeneratedAt() == null ? Instant.now().toString() : report.getGeneratedAt());
            logger.info("Archived MonthlyReport {}", report.getMonth());
            return report;
        } catch (Exception ex) {
            logger.error("Unexpected error during ArchiveReportProcessor", ex);
            report.setStatus("FAILED");
            report.setErrorMessage("Archive error: " + ex.getMessage());
            return report;
        }
    }
}
