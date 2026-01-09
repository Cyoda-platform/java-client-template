package com.example.application.processor;

import com.example.application.entity.alert.version_1.Alert;
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
import java.util.UUID;

/**
 * GenerateSARReport Processor - Alert Review and Case Management Workflow
 * 
 * Generates Suspicious Activity Report (SAR) for escalated cases.
 * Creates regulatory reporting documents for FinCEN submission.
 */
@Component
public class GenerateSARReport implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GenerateSARReport.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public GenerateSARReport(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Generating SAR report for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Alert.class)
                .validate(this::isValidEntityWithMetadata, "Invalid alert entity")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Alert> entityWithMetadata) {
        Alert entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Alert> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Alert> context) {

        EntityWithMetadata<Alert> entityWithMetadata = context.entityResponse();
        Alert alert = entityWithMetadata.entity();

        logger.debug("Generating SAR report for alert: {}", alert.getId());

        // Generate SAR report
        generateSARReport(alert);

        // Update metadata
        if (alert.getMetadata() == null) {
            alert.setMetadata(new HashMap<>());
        }
        alert.getMetadata().put("sarReportGenerated", true);
        alert.getMetadata().put("sarGenerationTime", LocalDateTime.now().toString());

        logger.info("SAR report generated for alert: {}", alert.getId());
        return entityWithMetadata;
    }

    private void generateSARReport(Alert alert) {
        if (alert.getMetadata() == null) {
            alert.setMetadata(new HashMap<>());
        }

        // Create SAR report metadata
        Map<String, Object> sarReport = new HashMap<>();
        
        // Report identification
        sarReport.put("reportId", "SAR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        sarReport.put("reportDate", LocalDateTime.now().toString());
        sarReport.put("reportingInstitution", "Compliance Management Platform");
        
        // Alert information
        sarReport.put("alertId", alert.getId());
        sarReport.put("alertType", alert.getAlertType());
        sarReport.put("severity", alert.getSeverity());
        
        // Subject information
        sarReport.put("subjectId", alert.getCustomerId());
        sarReport.put("transactionId", alert.getTransactionId());
        
        // Suspicious activity details
        Map<String, Object> activityDetails = new HashMap<>();
        activityDetails.put("activityType", alert.getAlertType());
        activityDetails.put("activityDate", LocalDateTime.now().toString());
        activityDetails.put("suspiciousIndicators", java.util.List.of(
            "Unusual transaction pattern",
            "High-risk jurisdiction involvement",
            "Watchlist match detected"
        ));
        sarReport.put("activityDetails", activityDetails);
        
        // Investigation findings
        Map<String, Object> findings = new HashMap<>();
        findings.put("investigationStatus", "ONGOING");
        findings.put("preliminaryConclusion", "Suspicious activity detected - requires further investigation");
        findings.put("recommendedAction", "ESCALATE_TO_REGULATOR");
        sarReport.put("findings", findings);
        
        // Regulatory information
        sarReport.put("filingDeadline", LocalDateTime.now().plusDays(30).toString());
        sarReport.put("reportingObligation", "FinCEN");
        sarReport.put("status", "DRAFT");

        alert.getMetadata().put("sarReport", sarReport);
    }
}

