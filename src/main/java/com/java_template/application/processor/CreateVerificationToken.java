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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CreateVerificationToken implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateVerificationToken.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateVerificationToken(SerializerFactory serializerFactory, EntityService entityService) {
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

        // Business logic:
        // 1. Generate a verification token and expiry
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);
        String expiresAtIso = expiresAt.toString();

        // 2. Attach token reference to consent (evidenceRef) and set status to pending_verification
        consent.setEvidenceRef(token);
        consent.setStatus("pending_verification");

        logger.info("Created verification token for consent {} token={} expiresAt={}", consent.getConsentId(), token, expiresAtIso);

        // 3. Persist an Audit record capturing token creation and expiry (adds an immutable record)
        try {
            Audit audit = new Audit();
            audit.setAudit_id(UUID.randomUUID().toString());
            audit.setAction("create_verification_token");
            // Use consent.userId as actor if present; otherwise use "system"
            String actor = consent.getUserId() != null && !consent.getUserId().isBlank() ? consent.getUserId() : "system";
            audit.setActor_id(actor);
            // entity_ref format: "<consentId>:Consent"
            audit.setEntity_ref(consent.getConsentId() + ":Consent");
            audit.setEvidence_ref(token);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("expires_at", expiresAtIso);
            metadata.put("consent_type", consent.getType());
            audit.setMetadata(metadata);
            audit.setTimestamp(Instant.now().toString());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                Audit.ENTITY_NAME,
                String.valueOf(Audit.ENTITY_VERSION),
                audit
            );

            // Log when added (do not block processor)
            idFuture.whenComplete((id, ex) -> {
                if (ex != null) {
                    logger.error("Failed to persist audit for consent {}: {}", consent.getConsentId(), ex.getMessage());
                } else {
                    logger.info("Persisted audit {} for consent {}", id, consent.getConsentId());
                }
            });
        } catch (Exception e) {
            logger.error("Error while creating audit record for consent {}: {}", consent.getConsentId(), e.getMessage(), e);
            // Do not throw; leave consent modified so Cyoda will persist its state.
        }

        // 4. Note: Sending of the verification email is handled by async processors listening for the token/evidence creation.
        // This processor only mutates the consent (evidenceRef, status) and records an audit.

        return consent;
    }
}