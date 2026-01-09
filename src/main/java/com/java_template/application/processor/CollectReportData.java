package com.java_template.application.processor;

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
 * CollectReportData Processor - Regulatory Reporting Workflow
 * 
 * Collects and aggregates data for regulatory reports (SAR/STR).
 * Gathers case information, evidence, and investigation findings.
 */
@Component
public class CollectReportData implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CollectReportData.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CollectReportData(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Collecting report data for request: {}", request.getId());

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

        logger.debug("Collecting report data for case: {}", caseEntity.getId());

        // Collect and aggregate report data
        collectReportData(caseEntity);

        // Update metadata
        if (caseEntity.getMetadata() == null) {
            caseEntity.setMetadata(new HashMap<>());
        }
        caseEntity.getMetadata().put("reportDataCollected", true);
        caseEntity.getMetadata().put("dataCollectionTime", LocalDateTime.now().toString());

        logger.info("Report data collected for case: {}", caseEntity.getId());
        return entityWithMetadata;
    }

    private void collectReportData(Case caseEntity) {
        if (caseEntity.getMetadata() == null) {
            caseEntity.setMetadata(new HashMap<>());
        }

        // Create report data structure
        Map<String, Object> reportData = new HashMap<>();

        // Case information
        reportData.put("caseId", caseEntity.getId());
        reportData.put("caseTitle", caseEntity.getTitle());
        reportData.put("caseDescription", caseEntity.getDescription());
        reportData.put("caseStatus", caseEntity.getStatus());
        reportData.put("createdAt", caseEntity.getCreatedAt());
        reportData.put("createdBy", caseEntity.getCreatedBy());

        // Alert information
        reportData.put("relatedAlerts", caseEntity.getAlerts());
        reportData.put("alertCount", caseEntity.getAlerts() != null ? caseEntity.getAlerts().size() : 0);

        // Evidence information
        reportData.put("relatedEvidence", caseEntity.getEvidences());
        reportData.put("evidenceCount", caseEntity.getEvidences() != null ? caseEntity.getEvidences().size() : 0);

        // Investigation summary
        Map<String, Object> investigationSummary = new HashMap<>();
        investigationSummary.put("investigationStatus", "COMPLETED");
        investigationSummary.put("investigationStartDate", caseEntity.getCreatedAt());
        investigationSummary.put("investigationEndDate", LocalDateTime.now());
        investigationSummary.put("investigationDurationDays", 5);
        investigationSummary.put("investigationFindings", "Suspicious activity confirmed - regulatory reporting required");
        reportData.put("investigationSummary", investigationSummary);

        // Regulatory information
        Map<String, Object> regulatoryInfo = new HashMap<>();
        regulatoryInfo.put("reportingObligation", "FinCEN");
        regulatoryInfo.put("reportType", "SAR");
        regulatoryInfo.put("filingDeadline", LocalDateTime.now().plusDays(30));
        regulatoryInfo.put("jurisdiction", "US");
        reportData.put("regulatoryInfo", regulatoryInfo);

        // Subject information
        if (caseEntity.getMetadata().containsKey("customerId")) {
            reportData.put("subjectId", caseEntity.getMetadata().get("customerId"));
        }

        caseEntity.getMetadata().put("reportData", reportData);
    }
}

