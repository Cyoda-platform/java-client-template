package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
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

@Component
public class InitEmailVerification implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(InitEmailVerification.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public InitEmailVerification(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        // Ensure emailVerified flag is set to false for newly registered users (email unverified)
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            user.setEmailVerified(false);
        }

        // If user opted into marketing (double opt-in flow expected), create a Consent record
        if (Boolean.TRUE.equals(user.getMarketingEnabled())) {
            Consent consent = new Consent();
            consent.setConsentId(UUID.randomUUID().toString());
            consent.setUserId(user.getUserId());
            consent.setType("marketing");
            consent.setStatus("requested");
            consent.setRequestedAt(Instant.now().toString());
            consent.setSource("signup_form");

            try {
                entityService.addItem(
                    Consent.ENTITY_NAME,
                    String.valueOf(Consent.ENTITY_VERSION),
                    consent
                ).thenAccept(id -> logger.info("Created Consent {} for user {}", id, user.getUserId()))
                 .exceptionally(ex -> { logger.error("Failed to create Consent for user {}", user.getUserId(), ex); return null; });
            } catch (Exception ex) {
                logger.error("Error invoking entityService.addItem for Consent", ex);
            }
        }

        // Append an Audit record for the init email verification action
        try {
            Audit audit = new Audit();
            audit.setAudit_id(UUID.randomUUID().toString());
            audit.setAction("init_email_verification");
            audit.setActor_id(user.getUserId());
            audit.setEntity_ref(user.getUserId() + ":User");
            audit.setTimestamp(Instant.now().toString());
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("email", user.getEmail());
            metadata.put("emailVerified", user.getEmailVerified());
            audit.setMetadata(metadata);

            entityService.addItem(
                Audit.ENTITY_NAME,
                String.valueOf(Audit.ENTITY_VERSION),
                audit
            ).thenAccept(id -> logger.info("Appended Audit {} for user {}", id, user.getUserId()))
             .exceptionally(ex -> { logger.error("Failed to append Audit for user {}", user.getUserId(), ex); return null; });
        } catch (Exception ex) {
            logger.error("Error invoking entityService.addItem for Audit", ex);
        }

        // Return mutated user object; Cyoda will persist changes to this entity automatically
        return user;
    }
}