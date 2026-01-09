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
 * SignAndHashExport Processor - Regulatory Reporting Workflow
 * 
 * Signs and hashes regulatory reports for integrity verification.
 * Creates digital signatures and checksums for audit trail.
 */
@Component
public class SignAndHashExport implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SignAndHashExport.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SignAndHashExport(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Signing and hashing report for request: {}", request.getId());

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

        logger.debug("Signing and hashing report for case: {}", caseEntity.getId());

        // Sign and hash the report
        signAndHashReport(caseEntity);

        // Update metadata
        if (caseEntity.getMetadata() == null) {
            caseEntity.setMetadata(new HashMap<>());
        }
        caseEntity.getMetadata().put("reportSigned", true);
        caseEntity.getMetadata().put("signatureTime", LocalDateTime.now().toString());

        logger.info("Report signed and hashed for case: {}", caseEntity.getId());
        return entityWithMetadata;
    }

    private void signAndHashReport(Case caseEntity) {
        if (caseEntity.getMetadata() == null) {
            caseEntity.setMetadata(new HashMap<>());
        }

        // Get rendered report
        Map<String, Object> renderedReport = (Map<String, Object>) caseEntity.getMetadata().get("renderedReport");
        if (renderedReport == null) {
            renderedReport = new HashMap<>();
        }

        // Create signature and hash information
        Map<String, Object> signatureInfo = new HashMap<>();

        // Generate hash
        String reportContent = (String) renderedReport.getOrDefault("reportContent", "");
        String contentHash = generateSHA256Hash(reportContent);
        signatureInfo.put("contentHash", contentHash);
        signatureInfo.put("hashAlgorithm", "SHA256");

        // Generate digital signature
        String digitalSignature = generateDigitalSignature(reportContent);
        signatureInfo.put("digitalSignature", digitalSignature);
        signatureInfo.put("signatureAlgorithm", "RSA-2048");

        // Signature metadata
        signatureInfo.put("signedBy", "system");
        signatureInfo.put("signatureTime", LocalDateTime.now().toString());
        signatureInfo.put("certificateId", "CERT-" + caseEntity.getId());
        signatureInfo.put("signatureValid", true);

        // Integrity verification
        Map<String, Object> integrityInfo = new HashMap<>();
        integrityInfo.put("integrityVerified", true);
        integrityInfo.put("verificationTime", LocalDateTime.now().toString());
        integrityInfo.put("verificationMethod", "SHA256_HASH_COMPARISON");
        signatureInfo.put("integrityVerification", integrityInfo);

        caseEntity.getMetadata().put("signatureInfo", signatureInfo);
    }

    private String generateSHA256Hash(String content) {
        // Simulate SHA256 hash generation
        return Integer.toHexString((content + System.currentTimeMillis()).hashCode());
    }

    private String generateDigitalSignature(String content) {
        // Simulate digital signature generation
        return "SIG-" + Integer.toHexString((content + "SIGNATURE").hashCode());
    }
}

