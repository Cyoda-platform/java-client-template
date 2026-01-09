package com.example.application.processor;

import com.example.application.entity.case_entity.version_1.Case;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * RenderTemplate Processor - Regulatory Reporting Workflow
 * 
 * Renders regulatory report templates with collected data.
 * Generates formatted SAR/STR documents for submission.
 */
@Component
public class RenderTemplate implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RenderTemplate.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RenderTemplate(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Rendering report template for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Case.class)
                .validate(this::isValidEntityWithMetadata, "Invalid case entity")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Case> entityWithMetadata) {
        Case entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Case> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Case> context) {

        EntityWithMetadata<Case> entityWithMetadata = context.entityResponse();
        Case caseEntity = entityWithMetadata.entity();

        logger.debug("Rendering template for case: {}", caseEntity.getId());

        // Render report template
        renderReportTemplate(caseEntity);

        // Update metadata
        if (caseEntity.getMetadata() == null) {
            caseEntity.setMetadata(new HashMap<>());
        }
        caseEntity.getMetadata().put("templateRendered", true);
        caseEntity.getMetadata().put("renderTime", LocalDateTime.now().toString());

        logger.info("Report template rendered for case: {}", caseEntity.getId());
        return entityWithMetadata;
    }

    private void renderReportTemplate(Case caseEntity) {
        if (caseEntity.getMetadata() == null) {
            caseEntity.setMetadata(new HashMap<>());
        }

        // Get collected report data
        Map<String, Object> reportData = (Map<String, Object>) caseEntity.getMetadata().get("reportData");
        if (reportData == null) {
            reportData = new HashMap<>();
        }

        // Create rendered report
        Map<String, Object> renderedReport = new HashMap<>();

        // Report header
        renderedReport.put("reportId", "SAR-" + caseEntity.getId());
        renderedReport.put("reportDate", LocalDateTime.now().toString());
        renderedReport.put("reportType", "Suspicious Activity Report (SAR)");
        renderedReport.put("version", "1.0");

        // Report body
        StringBuilder reportContent = new StringBuilder();
        reportContent.append("SUSPICIOUS ACTIVITY REPORT\n");
        reportContent.append("==========================\n\n");
        reportContent.append("Report ID: ").append(caseEntity.getId()).append("\n");
        reportContent.append("Report Date: ").append(LocalDateTime.now()).append("\n");
        reportContent.append("Case Title: ").append(caseEntity.getTitle()).append("\n");
        reportContent.append("Case Description: ").append(caseEntity.getDescription()).append("\n\n");

        reportContent.append("INVESTIGATION SUMMARY\n");
        reportContent.append("---------------------\n");
        if (reportData.containsKey("investigationSummary")) {
            Map<String, Object> invSummary = (Map<String, Object>) reportData.get("investigationSummary");
            reportContent.append("Status: ").append(invSummary.get("investigationStatus")).append("\n");
            reportContent.append("Findings: ").append(invSummary.get("investigationFindings")).append("\n");
        }

        reportContent.append("\nRELATED ALERTS\n");
        reportContent.append("--------------\n");
        if (reportData.containsKey("alertCount")) {
            reportContent.append("Total Alerts: ").append(reportData.get("alertCount")).append("\n");
        }

        reportContent.append("\nEVIDENCE\n");
        reportContent.append("--------\n");
        if (reportData.containsKey("evidenceCount")) {
            reportContent.append("Total Evidence Documents: ").append(reportData.get("evidenceCount")).append("\n");
        }

        reportContent.append("\nREGULATORY INFORMATION\n");
        reportContent.append("---------------------\n");
        if (reportData.containsKey("regulatoryInfo")) {
            Map<String, Object> regInfo = (Map<String, Object>) reportData.get("regulatoryInfo");
            reportContent.append("Reporting Obligation: ").append(regInfo.get("reportingObligation")).append("\n");
            reportContent.append("Filing Deadline: ").append(regInfo.get("filingDeadline")).append("\n");
        }

        renderedReport.put("reportContent", reportContent.toString());
        renderedReport.put("contentFormat", "TEXT");
        renderedReport.put("status", "READY_FOR_REVIEW");

        caseEntity.getMetadata().put("renderedReport", renderedReport);
    }
}

