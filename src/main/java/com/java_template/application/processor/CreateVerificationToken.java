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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class CreateVerificationToken implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateVerificationToken.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CreateVerificationToken(SerializerFactory serializerFactory,
                                   EntityService entityService,
                                   ObjectMapper objectMapper) {
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
            .map(this::processEntityLogic)
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
        // 2. Save token as evidence_ref on the consent and set status -> pending_verification
        // 3. Persist an Audit record with token as evidence_ref and expiry metadata
        // 4. (Send email is handled by an async processor; here we only prepare and record)

        // Generate token and expiry
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(1, ChronoUnit.DAYS); // token valid for 1 day
        String expiresAtIso = expiresAt.toString();

        // Update consent state (this entity will be persisted by Cyoda workflow)
        consent.setEvidence_ref(token);
        consent.setStatus("pending_verification");

        // Create an Audit entry to record creation of verification token
        Audit audit = new Audit();
        audit.setAuditId(UUID.randomUUID().toString());
        audit.setAction("create_verification_token");
        // actor is system for automated token creation
        audit.setActorId("system");
        // reference the consent that triggered the token creation
        String entityRef = (consent.getConsent_id() != null ? consent.getConsent_id() : "unknown") + ":Consent";
        audit.setEntityRef(entityRef);
        audit.setEvidenceRef(token);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("expires_at", expiresAtIso);
        metadata.put("consent_type", consent.getType());
        metadata.put("user_id", consent.getUser_id());
        audit.setMetadata(metadata);
        audit.setTimestamp(now.toString());

        // Persist the Audit record using EntityService (non-blocking call, but we wait to ensure record exists)
        try {
            CompletableFuture<java.util.UUID> future = entityService.addItem(
                Audit.ENTITY_NAME,
                Audit.ENTITY_VERSION,
                audit
            );
            // Wait for completion to ensure audit recorded; if it fails we'll log the error but still return updated consent
            future.get();
            logger.info("Audit record created for consent {} with token expiry {}", consent.getConsent_id(), expiresAtIso);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating audit for consent {}: {}", consent.getConsent_id(), ie.getMessage(), ie);
        } catch (ExecutionException ee) {
            logger.error("Failed to persist audit for consent {}: {}", consent.getConsent_id(), ee.getMessage(), ee);
        } catch (Exception ex) {
            logger.error("Unexpected error while creating audit for consent {}: {}", consent.getConsent_id(), ex.getMessage(), ex);
        }

        // At this point, the consent entity is updated (evidence_ref + status).
        // An asynchronous processor (send_verification_email) should pick up the consent change and send the email.
        logger.info("Created verification token for consent {} (user {}). Token expires at {}", consent.getConsent_id(), consent.getUser_id(), expiresAtIso);

        return consent;
    }
}