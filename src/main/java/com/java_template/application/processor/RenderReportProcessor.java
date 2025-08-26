package com.java_template.application.processor;
import com.java_template.application.entity.monthlyreport.version_1.MonthlyReport;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

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

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(MonthlyReport.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(MonthlyReport entity) {
        if (entity == null) return false;
        // Required pre-render fields: month, generatedAt, and metrics must be present and consistent.
        if (entity.getMonth() == null || entity.getMonth().isBlank()) return false;
        if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) return false;
        if (entity.getTotalUsers() == null || entity.getTotalUsers() < 0) return false;
        if (entity.getNewUsers() == null || entity.getNewUsers() < 0) return false;
        if (entity.getInvalidUsers() == null || entity.getInvalidUsers() < 0) return false;
        // Consistency: totalUsers == newUsers + invalidUsers
        if (!Objects.equals(entity.getTotalUsers().intValue(), entity.getNewUsers().intValue() + entity.getInvalidUsers().intValue())) {
            return false;
        }
        // At this stage fileRef may not be set yet and status will be set by this processor.
        return true;
    }

    private MonthlyReport processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<MonthlyReport> context) {
        MonthlyReport entity = context.entity();
        logger.info("Rendering report for month {}", entity.getMonth());

        // Ensure generatedAt is set (if upstream didn't set it).
        if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
            String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            entity.setGeneratedAt(now);
        }

        try {
            // Simple deterministic fileRef generation based on month.
            // In a real implementation this would render a file (PDF/CSV) and upload to storage.
            String sanitizedMonth = entity.getMonth().replaceAll("[^0-9-]", "");
            String fileRef = String.format("reports/%s-user-report.pdf", sanitizedMonth);
            entity.setFileRef(fileRef);

            // Mark report as READY after rendering
            entity.setStatus("READY");

            logger.info("Report rendered for month {} -> fileRef={}", entity.getMonth(), fileRef);
        } catch (Exception ex) {
            logger.error("Failed to render report for month {}: {}", entity.getMonth(), ex.getMessage(), ex);
            // On failure mark report as FAILED. Do not throw; return modified entity so Cyoda can persist state.
            entity.setStatus("FAILED");
            // Ensure fileRef is null/empty on failure
            entity.setFileRef(null);
        }

        return entity;
    }
}