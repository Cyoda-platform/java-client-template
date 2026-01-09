package com.example.application.processor;

import com.example.application.entity.alert.version_1.Alert;
import com.example.application.entity.case_entity.version_1.Case;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CreateCase Processor - Alert Review and Case Management Workflow
 * 
 * Creates compliance cases from alerts requiring escalation.
 * Initializes case records with alert linkage and investigation tracking.
 */
@Component
public class CreateCase implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateCase.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateCase(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Creating case for request: {}", request.getId());

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

        logger.debug("Creating case for alert: {}", alert.getId());

        // Create compliance case from alert
        createComplianceCase(alert);

        return entityWithMetadata;
    }

    private void createComplianceCase(Alert alert) {
        try {
            Case complianceCase = new Case();
            complianceCase.setId("CASE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            complianceCase.setTitle("Investigation - " + alert.getAlertType() + " - " + alert.getId());
            complianceCase.setDescription("Compliance case created from alert. Alert Type: " + alert.getAlertType() +
                    ", Severity: " + alert.getSeverity() + ", Transaction ID: " + alert.getTransactionId());
            complianceCase.setCreatedBy(alert.getAssignedTo() != null ? alert.getAssignedTo() : "system");
            complianceCase.setAssignees(new ArrayList<>());
            if (alert.getAssignedTo() != null) {
                complianceCase.getAssignees().add(alert.getAssignedTo());
            }
            complianceCase.setStatus(Case.CaseStatus.OPEN);
            complianceCase.setAlerts(new ArrayList<>());
            complianceCase.getAlerts().add(alert.getId());
            complianceCase.setEvidences(new ArrayList<>());
            complianceCase.setCreatedAt(LocalDateTime.now());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("alertId", alert.getId());
            metadata.put("alertType", alert.getAlertType());
            metadata.put("severity", alert.getSeverity());
            metadata.put("customerId", alert.getCustomerId());
            metadata.put("transactionId", alert.getTransactionId());
            metadata.put("caseType", "ALERT_ESCALATION");
            metadata.put("priority", alert.getSeverity() == Alert.Severity.HIGH ? "HIGH" : "MEDIUM");
            metadata.put("createdFromAlert", true);
            complianceCase.setMetadata(metadata);

            entityService.create(complianceCase);
            logger.info("Compliance case created: {} from alert: {}", complianceCase.getId(), alert.getId());

            // Update alert with case reference
            alert.setRelatedCaseId(complianceCase.getId());
            if (alert.getMetadata() == null) {
                alert.setMetadata(new HashMap<>());
            }
            alert.getMetadata().put("relatedCaseId", complianceCase.getId());
        } catch (Exception e) {
            logger.error("Failed to create case for alert: {}", alert.getId(), e);
        }
    }
}

