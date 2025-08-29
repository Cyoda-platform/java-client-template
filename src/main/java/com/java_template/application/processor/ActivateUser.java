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
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ActivateUser implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ActivateUser.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ActivateUser(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        try {
            // Requirement: Activation occurs only when email verified and consent double opt-in confirmed.
            // Check emailVerified flag
            if (user.getEmailVerified() == null || !user.getEmailVerified()) {
                logger.info("User {} is not email verified. Skipping activation.", user.getUserId());
                return user;
            }

            // Fetch consents for this user
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.user_id", "EQUALS", user.getUserId())
            );

            CompletableFuture<List<DataPayload>> consentsFuture = entityService.getItemsByCondition(
                Consent.ENTITY_NAME,
                Consent.ENTITY_VERSION,
                condition,
                true
            );

            List<DataPayload> dataPayloads = consentsFuture.get();
            if (dataPayloads == null || dataPayloads.isEmpty()) {
                logger.info("No consents found for user {}. Cannot activate.", user.getUserId());
                return user;
            }

            boolean hasConfirmedConsent = false;
            boolean marketingConsent = false;
            List<String> consentIds = new ArrayList<>();

            for (DataPayload payload : dataPayloads) {
                try {
                    Consent consent = objectMapper.treeToValue(payload.getData(), Consent.class);
                    if (consent == null) continue;
                    String status = consent.getStatus();
                    if (status != null) {
                        String s = status.trim().toLowerCase();
                        if (s.equals("active") || s.equals("granted") || s.equals("confirmed")) {
                            hasConfirmedConsent = true;
                            consentIds.add(consent.getConsent_id());
                            if (consent.getType() != null && consent.getType().equalsIgnoreCase("marketing")) {
                                marketingConsent = true;
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to parse consent payload for user {}: {}", user.getUserId(), ex.getMessage());
                }
            }

            if (!hasConfirmedConsent) {
                logger.info("User {} does not have confirmed consent. Skipping activation.", user.getUserId());
                return user;
            }

            // At this point: email verified AND consent confirmed -> mark user as active.
            // The User entity does not have an explicit 'status' field; use gdprState to mark lifecycle state as 'active'.
            user.setGdprState("active");

            // Set marketingEnabled according to consent type if applicable
            if (marketingConsent) {
                user.setMarketingEnabled(Boolean.TRUE);
            }

            // Append an audit record for activation
            Audit audit = new Audit();
            String auditId = UUID.randomUUID().toString();
            audit.setAuditId(auditId);
            audit.setAction("activate_user");
            audit.setActorId(user.getUserId());
            audit.setEntityRef(user.getUserId() + ":User");
            audit.setTimestamp(Instant.now().toString());
            if (consentIds.size() == 1) {
                audit.setEvidenceRef(consentIds.get(0));
            } else if (!consentIds.isEmpty()) {
                // put consent ids into metadata
                java.util.Map<String, Object> metadata = new java.util.HashMap<>();
                metadata.put("consent_ids", consentIds);
                audit.setMetadata(metadata);
            }

            try {
                CompletableFuture<java.util.UUID> added = entityService.addItem(
                    Audit.ENTITY_NAME,
                    Audit.ENTITY_VERSION,
                    audit
                );
                added.get();

                // Add audit ref to user.auditRefs
                List<String> auditRefs = user.getAuditRefs();
                if (auditRefs == null) {
                    auditRefs = new ArrayList<>();
                    user.setAuditRefs(auditRefs);
                }
                auditRefs.add(auditId);
            } catch (Exception ex) {
                logger.error("Failed to persist audit for user {}: {}", user.getUserId(), ex.getMessage(), ex);
                // Even if audit persistence fails, keep user state changes (framework will persist user)
            }

        } catch (Exception e) {
            logger.error("Error while activating user {}: {}", user != null ? user.getUserId() : "unknown", e.getMessage(), e);
            // In case of unexpected error, return user unchanged
        }

        return user;
    }
}