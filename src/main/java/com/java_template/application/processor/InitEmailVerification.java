package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
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
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

@Component
public class InitEmailVerification implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(InitEmailVerification.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public InitEmailVerification(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(User.class)
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

    private boolean isValidEntity(User entity) {
        return entity != null && entity.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User user = context.entity();

        // Business logic for initializing email verification on new registrations:
        // - Ensure the user emailVerified flag is set to false (email_unverified state)
        // - Set gdprState to "email_unverified" if not already set
        // - If user has marketingEnabled == true, create a Consent record in pending_verification state (double opt-in)
        // - Create an Audit record for this initialization and attach audit ref to the user.auditRefs list
        // Note: We do NOT persist changes to the triggering User entity directly here; Cyoda will persist it automatically.

        try {
            // Ensure emailVerified is explicitly false (unverified)
            if (user.getEmailVerified() == null || Boolean.TRUE.equals(user.getEmailVerified())) {
                user.setEmailVerified(false);
            }

            // Set gdprState to indicate email unverified if not already set
            if (user.getGdprState() == null || user.getGdprState().isBlank()) {
                user.setGdprState("email_unverified");
            }

            // Prepare and append an Audit record
            String auditId = UUID.randomUUID().toString();
            Audit audit = new Audit();
            audit.setAuditId(auditId);
            audit.setAction("init_email_verification");
            audit.setActorId(user.getUserId() != null ? user.getUserId() : "system");
            audit.setEntityRef((user.getUserId() != null ? user.getUserId() : "") + ":User");
            audit.setTimestamp(Instant.now().toString());
            // include minimal metadata
            Map<String, Object> metadata = Map.of(
                    "email", user.getEmail() != null ? user.getEmail() : "",
                    "marketingEnabled", String.valueOf(user.getMarketingEnabled())
            );
            audit.setMetadata(metadata);

            // Persist audit using EntityService
            try {
                CompletableFuture<UUID> auditFuture = entityService.addItem(
                        Audit.ENTITY_NAME,
                        Audit.ENTITY_VERSION,
                        audit
                );
                UUID persistedAuditId = auditFuture.get();
                logger.info("Appended Audit {} for user {}", persistedAuditId, user.getUserId());
            } catch (InterruptedException | ExecutionException ex) {
                logger.error("Failed to persist Audit for user {}: {}", user.getUserId(), ex.getMessage(), ex);
            }

            // Add audit ref to user.auditRefs
            List<String> auditRefs = user.getAuditRefs();
            if (auditRefs == null) {
                auditRefs = new ArrayList<>();
            }
            auditRefs.add(auditId);
            user.setAuditRefs(auditRefs);

            // If marketing consent requested (double opt-in), create Consent record in pending_verification
            if (Boolean.TRUE.equals(user.getMarketingEnabled())) {
                String consentId = UUID.randomUUID().toString();
                Consent consent = new Consent();
                consent.setConsent_id(consentId);
                consent.setUser_id(user.getUserId());
                consent.setRequested_at(Instant.now().toString());
                // Use pending_verification to indicate double opt-in required
                consent.setStatus("pending_verification");
                consent.setType("marketing");
                consent.setSource("init_email_verification");

                try {
                    CompletableFuture<UUID> consentFuture = entityService.addItem(
                            Consent.ENTITY_NAME,
                            Consent.ENTITY_VERSION,
                            consent
                    );
                    UUID persistedConsentId = consentFuture.get();
                    logger.info("Created Consent {} for user {}", persistedConsentId, user.getUserId());
                } catch (InterruptedException | ExecutionException ex) {
                    logger.error("Failed to persist Consent for user {}: {}", user.getUserId(), ex.getMessage(), ex);
                }
            }

        } catch (Exception ex) {
            logger.error("Unexpected error while initializing email verification for user {}: {}", user != null ? user.getUserId() : "unknown", ex.getMessage(), ex);
            // Do not throw; return the entity in its best-effort updated state.
        }

        return user;
    }
}