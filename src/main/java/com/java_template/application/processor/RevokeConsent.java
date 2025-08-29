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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class RevokeConsent implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RevokeConsent.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
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
        Consent entity = context.entity();

        // Business logic:
        // - Only active consents should be transitioned to revoked on user request.
        // - Record an audit entry for the revoke action (or attempted revoke).
        // - Set revoked_at timestamp when revocation occurs.
        if (entity == null) {
            logger.warn("Consent entity is null in processing context");
            return null;
        }

        String previousStatus = entity.getStatus();
        String actorId = entity.getUser_id() != null ? entity.getUser_id() : "system";
        Instant now = Instant.now();

        // Prepare audit entry common fields
        Audit audit = new Audit();
        audit.setAuditId(UUID.randomUUID().toString());
        audit.setActorId(actorId);
        audit.setEntityRef(entity.getConsent_id() != null ? entity.getConsent_id() + ":Consent" : ("unknown:Consent"));
        audit.setTimestamp(now.toString());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("previous_status", previousStatus);
        metadata.put("type", entity.getType());
        audit.setMetadata(metadata);

        try {
            if (previousStatus != null && previousStatus.equalsIgnoreCase("active")) {
                // Perform revocation
                entity.setStatus("revoked");
                entity.setRevoked_at(now.toString());
                audit.setAction("revoke_consent");
                audit.setEvidenceRef(entity.getEvidence_ref());

                // persist audit
                try {
                    entityService.addItem(Audit.ENTITY_NAME, Audit.ENTITY_VERSION, audit).get();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted while persisting audit for revoke_consent: {}", ie.getMessage(), ie);
                } catch (ExecutionException ee) {
                    logger.warn("Failed to persist audit for revoke_consent: {}", ee.getMessage(), ee);
                }

                logger.info("Consent {} revoked for user {}", entity.getConsent_id(), actorId);
            } else {
                // Not in active state - record attempted revoke
                audit.setAction("revoke_consent_attempt");
                audit.setEvidenceRef(entity.getEvidence_ref());
                metadata.put("attempt_result", "not_active");
                audit.setMetadata(metadata);

                try {
                    entityService.addItem(Audit.ENTITY_NAME, Audit.ENTITY_VERSION, audit).get();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted while persisting audit for revoke_consent_attempt: {}", ie.getMessage(), ie);
                } catch (ExecutionException ee) {
                    logger.warn("Failed to persist audit for revoke_consent_attempt: {}", ee.getMessage(), ee);
                }

                logger.warn("Consent {} revoke attempted but consent was not active (status={})", entity.getConsent_id(), previousStatus);
            }
        } catch (Exception ex) {
            logger.error("Unexpected error processing revoke consent for {}: {}", entity.getConsent_id(), ex.getMessage(), ex);
        }

        return entity;
    }
}