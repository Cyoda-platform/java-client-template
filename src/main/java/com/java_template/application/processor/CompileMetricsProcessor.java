package com.java_template.application.processor;

import com.java_template.application.entity.monthlyreport.version_1.MonthlyReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
public class CompileMetricsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompileMetricsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CompileMetricsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MonthlyReport for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(MonthlyReport.class)
            .validate(this::isValidEntity, "Invalid MonthlyReport state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(MonthlyReport entity) {
        // Basic validation: must have month to compile metrics for
        return entity != null && entity.getMonth() != null && !entity.getMonth().isBlank();
    }

    private MonthlyReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<MonthlyReport> context) {
        MonthlyReport report = context.entity();

        // Set generatedAt if missing
        if (report.getGeneratedAt() == null || report.getGeneratedAt().isBlank()) {
            report.setGeneratedAt(Instant.now().toString());
        }

        // Metrics calculation:
        // If metric fields are already set (e.g., by earlier steps) keep them.
        // Otherwise initialize to 0. In a full implementation we would query User entities via entityService
        // and compute totals for report.getMonth(). Here we provide safe defaults and set the processing status.
        if (report.getTotalUsers() == null) {
            report.setTotalUsers(0);
        }
        if (report.getNewUsers() == null) {
            report.setNewUsers(0);
        }
        if (report.getInvalidUsers() == null) {
            report.setInvalidUsers(0);
        }

        // Mark report as GENERATING while downstream processors (rendering) operate on it.
        report.setStatus("GENERATING");

        // Ensure fileRef is not null to avoid null issues downstream; real fileRef will be produced by RenderReportProcessor
        if (report.getFileRef() == null) {
            report.setFileRef("");
        }

        logger.info("Compiled metrics for month {}: total={}, new={}, invalid={}",
            report.getMonth(), report.getTotalUsers(), report.getNewUsers(), report.getInvalidUsers());

        return report;
    }
}