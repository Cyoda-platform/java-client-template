package com.java_template.application.processor;

import com.java_template.application.entity.consent.version_1.Consent;
import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

@Component
public class RecordEvidenceAndActivate implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RecordEvidenceAndActivate.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public RecordEvidenceAndActivate(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Consent for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Consent.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Consent entity) {
        return entity != null && entity.isValid();
    }

    private Consent processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Consent> context) {
        Consent consent = context.entity();

        // Record evidence if not already present
        try {
            String evidence = consent.getEvidence_ref();
            if (evidence == null || evidence.isBlank()) {
                evidence = UUID.randomUUID().toString();
                consent.setEvidence_ref(evidence);
            }

            // Mark consent as granted now and activate
            String now = Instant.now().toString();
            consent.setGranted_at(now);
            consent.setStatus("active");

            // Append an audit record for this activation (immutable)
            Audit audit = new Audit();
            audit.setAuditId(UUID.randomUUID().toString());
            audit.setAction("consent_verified");
            String actor = consent.getUser_id() != null && !consent.getUser_id().isBlank() ? consent.getUser_id() : "system";
            audit.setActorId(actor);
            audit.setEntityRef((consent.getConsent_id() != null ? consent.getConsent_id() : "") + ":Consent");
            audit.setEvidenceRef(consent.getEvidence_ref());
            audit.setTimestamp(now);

            Map<String, Object> metadata = new HashMap<>();
            if (consent.getType() != null) metadata.put("type", consent.getType());
            if (consent.getSource() != null) metadata.put("source", consent.getSource());
            metadata.put("recorded_by", actor);
            audit.setMetadata(metadata);

            // Persist audit entry asynchronously via EntityService
            try {
                CompletableFuture<UUID> idFuture = entityService.addItem(
                    Audit.ENTITY_NAME,
                    Audit.ENTITY_VERSION,
                    audit
                );
                // Ensure the add completed; log but do not fail the consent activation if audit persistence fails
                UUID auditId = idFuture.get();
                logger.info("Appended audit {} for consent {}", auditId, consent.getConsent_id());
            } catch (Exception e) {
                logger.error("Failed to persist audit for consent {}: {}", consent.getConsent_id(), e.getMessage(), e);
            }

        } catch (Exception ex) {
            // Log but do not throw to allow the serializer to handle transactional semantics
            logger.error("Error while recording evidence and activating consent {}: {}", consent != null ? consent.getConsent_id() : null, ex.getMessage(), ex);
        }

        return consent;
    }
}