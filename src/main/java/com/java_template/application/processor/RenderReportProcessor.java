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

@Component
public class RenderReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RenderReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RenderReportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MonthlyReport for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(MonthlyReport.class)
            .validate(this::isValidEntity, "Invalid monthly report state for rendering")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(MonthlyReport report) {
        if (report == null) return false;
        // Required basic fields for rendering: month, generatedAt, numeric metrics and status
        if (report.getMonth() == null || report.getMonth().isBlank()) return false;
        if (report.getGeneratedAt() == null || report.getGeneratedAt().isBlank()) return false;
        if (report.getTotalUsers() == null || report.getNewUsers() == null || report.getInvalidUsers() == null) return false;
        if (report.getTotalUsers() < 0 || report.getNewUsers() < 0 || report.getInvalidUsers() < 0) return false;
        // Consistency check
        if (report.getTotalUsers().intValue() != (report.getNewUsers().intValue() + report.getInvalidUsers().intValue())) return false;
        // Expect the report to be in a generation phase before rendering
        String status = report.getStatus();
        if (status == null || status.isBlank()) return false;
        String normalized = status.trim().toUpperCase();
        // Accept either GENERATING or RENDERING as valid pre-render states
        return "GENERATING".equals(normalized) || "RENDERING".equals(normalized);
    }

    private MonthlyReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<MonthlyReport> context) {
        MonthlyReport report = context.entity();

        try {
            // Render the report artifact (simulated).
            // Create a deterministic file reference based on month and a simple suffix.
            // Do not modify generatedAt; assume it was set by earlier processor.
            String month = report.getMonth().replaceAll("[^0-9\\-]", "");
            String fileRef = String.format("reports/%s-user-report.pdf", month);
            report.setFileRef(fileRef);

            // Mark report as READY
            report.setStatus("READY");

            logger.info("Rendered report for month {} into fileRef={}", report.getMonth(), fileRef);
        } catch (Exception ex) {
            logger.error("Failed to render report for month {}: {}", report != null ? report.getMonth() : "unknown", ex.getMessage(), ex);
            // On failure set status to FAILED and populate summary-like field if available as fileRef (no summary field on MonthlyReport).
            if (report != null) {
                report.setStatus("FAILED");
                report.setFileRef(null);
            }
        }

        return report;
    }
}