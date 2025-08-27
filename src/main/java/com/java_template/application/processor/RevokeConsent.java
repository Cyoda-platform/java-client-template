package com.java_template.application.processor;

import com.java_template.application.entity.consent.version_1.Consent;
import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class RevokeConsent implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RevokeConsent.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public RevokeConsent(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Consent for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Consent.class)
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

        // If already revoked, no further action required
        if (consent.getStatus() != null && "revoked".equalsIgnoreCase(consent.getStatus())) {
            logger.debug("Consent {} is already revoked", consent.getConsentId());
            return consent;
        }

        // Mark as revoked and set revoked timestamp if not present
        consent.setStatus("revoked");
        if (consent.getRevokedAt() == null || consent.getRevokedAt().isBlank()) {
            consent.setRevokedAt(Instant.now().toString());
        }

        // Create an Audit entry for the revoke action. Actor is set to "system" for processor-invoked actions.
        try {
            Audit audit = new Audit();
            audit.setAudit_id(UUID.randomUUID().toString());
            audit.setAction("revoke_consent");
            audit.setActor_id("system");
            String entityRef = (consent.getConsentId() != null ? consent.getConsentId() : UUID.randomUUID().toString())
                    + ":Consent";
            audit.setEntity_ref(entityRef);
            // carry evidence ref if present on consent
            audit.setEvidence_ref(consent.getEvidenceRef());
            audit.setTimestamp(Instant.now().toString());

            Map<String, Object> metadata = new HashMap<>();
            if (consent.getUserId() != null) metadata.put("userId", consent.getUserId());
            if (consent.getType() != null) metadata.put("type", consent.getType());
            if (consent.getSource() != null) metadata.put("source", consent.getSource());
            audit.setMetadata(metadata);

            // Persist audit asynchronously; do not block the processor.
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                Audit.ENTITY_NAME,
                String.valueOf(Audit.ENTITY_VERSION),
                audit
            );
            idFuture.whenComplete((id, ex) -> {
                if (ex != null) {
                    logger.error("Failed to append audit for consent {}: {}", consent.getConsentId(), ex.getMessage());
                } else {
                    logger.debug("Appended audit {} for consent {}", id, consent.getConsentId());
                }
            });
        } catch (Exception e) {
            logger.error("Error while creating audit for revoke consent {}: {}", consent.getConsentId(), e.getMessage());
        }

        // Return the modified consent - Cyoda will persist the triggering entity automatically
        return consent;
    }
}