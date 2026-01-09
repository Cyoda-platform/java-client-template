package com.java_template.application.processor;

import com.example.application.entity.alert.version_1.Alert;
import com.example.application.entity.document_evidence.version_1.DocumentEvidence;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AttachEvidence Processor - Alert Review and Case Management Workflow
 * 
 * Attaches evidence documents to compliance cases.
 * Creates DocumentEvidence records linked to case investigations.
 */
@Component
public class AttachEvidence implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AttachEvidence.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AttachEvidence(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Attaching evidence for request: {}", request.getId());

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

        logger.debug("Attaching evidence for alert: {}", alert.getId());

        // Create and attach evidence documents
        if (alert.getRelatedCaseId() != null) {
            attachEvidenceToCase(alert);
        }

        // Update metadata
        if (alert.getMetadata() == null) {
            alert.setMetadata(new HashMap<>());
        }
        alert.getMetadata().put("evidenceAttached", true);
        alert.getMetadata().put("evidenceAttachmentTime", System.currentTimeMillis());

        logger.info("Evidence attached for alert: {}", alert.getId());
        return entityWithMetadata;
    }

    private void attachEvidenceToCase(Alert alert) {
        try {
            // Create evidence document for transaction data
            DocumentEvidence evidence = new DocumentEvidence();
            evidence.setId("DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            evidence.setCaseId(alert.getRelatedCaseId());
            evidence.setUploadedBy("system");
            evidence.setFilename("alert_evidence_" + alert.getId() + ".json");
            evidence.setMimeType("application/json");
            evidence.setSizeBytes(4096L);
            evidence.setStoragePath("/compliance/cases/" + alert.getRelatedCaseId() + "/evidence/" + evidence.getId());
            evidence.setChecksum(generateChecksum(alert.getId()));
            evidence.setUploadedAt(LocalDateTime.now());
            evidence.setTags(java.util.List.of("ALERT_EVIDENCE", "TRANSACTION_DATA", "COMPLIANCE"));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("alertId", alert.getId());
            metadata.put("alertType", alert.getAlertType());
            metadata.put("severity", alert.getSeverity());
            metadata.put("transactionId", alert.getTransactionId());
            metadata.put("customerId", alert.getCustomerId());
            evidence.setMetadata(metadata);

            entityService.create(evidence);
            logger.info("Evidence document created: {} for case: {}", evidence.getId(), alert.getRelatedCaseId());

            // Update alert metadata with evidence reference
            if (alert.getMetadata() == null) {
                alert.setMetadata(new HashMap<>());
            }
            alert.getMetadata().put("evidenceDocumentId", evidence.getId());
        } catch (Exception e) {
            logger.error("Failed to attach evidence for alert: {}", alert.getId(), e);
        }
    }

    private String generateChecksum(String alertId) {
        return Integer.toHexString((alertId + System.currentTimeMillis()).hashCode());
    }
}

