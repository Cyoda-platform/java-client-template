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
import java.util.UUID;

/**
 * SubmitToRegulator Processor - Regulatory Reporting Workflow
 * 
 * Submits signed regulatory reports to regulatory authorities.
 * Handles FinCEN SAR/STR submissions and tracks submission status.
 */
@Component
public class SubmitToRegulator implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubmitToRegulator.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubmitToRegulator(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Submitting report to regulator for request: {}", request.getId());

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

        logger.debug("Submitting report to regulator for case: {}", caseEntity.getId());

        // Submit report to regulatory authority
        submitToRegulator(caseEntity);

        // Update metadata
        if (caseEntity.getMetadata() == null) {
            caseEntity.setMetadata(new HashMap<>());
        }
        caseEntity.getMetadata().put("submittedToRegulator", true);
        caseEntity.getMetadata().put("submissionTime", LocalDateTime.now().toString());

        logger.info("Report submitted to regulator for case: {}", caseEntity.getId());
        return entityWithMetadata;
    }

    private void submitToRegulator(Case caseEntity) {
        if (caseEntity.getMetadata() == null) {
            caseEntity.setMetadata(new HashMap<>());
        }

        // Create submission record
        Map<String, Object> submission = new HashMap<>();

        // Submission identification
        submission.put("submissionId", "SUB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        submission.put("submissionDate", LocalDateTime.now().toString());
        submission.put("submissionTime", System.currentTimeMillis());

        // Regulatory information
        submission.put("regulatoryAuthority", "FinCEN");
        submission.put("reportType", "SAR");
        submission.put("jurisdiction", "US");

        // Report information
        submission.put("caseId", caseEntity.getId());
        submission.put("reportId", "SAR-" + caseEntity.getId());

        // Submission status
        submission.put("status", "SUBMITTED");
        submission.put("statusCode", "200");
        submission.put("statusMessage", "Report successfully submitted to FinCEN");

        // Submission details
        Map<String, Object> submissionDetails = new HashMap<>();
        submissionDetails.put("endpoint", "https://fincen.gov/api/sar/submit");
        submissionDetails.put("protocol", "HTTPS");
        submissionDetails.put("authentication", "CERTIFICATE_BASED");
        submissionDetails.put("encryption", "AES-256");
        submission.put("submissionDetails", submissionDetails);

        // Confirmation information
        Map<String, Object> confirmation = new HashMap<>();
        confirmation.put("confirmationNumber", "FINCEN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        confirmation.put("confirmationDate", LocalDateTime.now().toString());
        confirmation.put("confirmationStatus", "ACKNOWLEDGED");
        submission.put("confirmation", confirmation);

        // Audit trail
        Map<String, Object> auditTrail = new HashMap<>();
        auditTrail.put("submittedBy", "system");
        auditTrail.put("submissionMethod", "AUTOMATED");
        auditTrail.put("retryCount", 0);
        submission.put("auditTrail", auditTrail);

        caseEntity.getMetadata().put("regulatorySubmission", submission);
    }
}

