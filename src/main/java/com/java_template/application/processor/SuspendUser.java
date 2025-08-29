package com.java_template.application.processor;

import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class SuspendUser implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SuspendUser.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SuspendUser(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
            .map(ctx -> processEntityLogic(ctx, request.getId())) // pass request id explicitly
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User entity) {
        return entity != null && entity.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context, String requestId) {
        User user = context.entity();

        // Business logic for suspending a user (admin action)
        // 1. Record previous relevant state for audit metadata
        String previousGdprState = user.getGdprState();
        Boolean previousMarketingEnabled = user.getMarketingEnabled();

        // 2. Apply suspension: mark gdprState as "suspended" and disable marketing
        user.setGdprState("suspended");
        user.setMarketingEnabled(Boolean.FALSE);

        // 3. Create an Audit record to append immutably via EntityService
        Audit audit = new Audit();
        audit.setAuditId(UUID.randomUUID().toString());
        audit.setAction("suspend_user");
        // Actor is an admin-initiated action per functional requirements; using "Admin" as actor identifier
        audit.setActorId("Admin");
        audit.setEntityRef(user.getUserId() + ":User");
        audit.setTimestamp(Instant.now().toString());

        // metadata: include previous state for traceability
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("previous_gdpr_state", previousGdprState);
        metadata.put("previous_marketing_enabled", previousMarketingEnabled);
        metadata.put("trigger_request_id", requestId);
        audit.setMetadata(metadata);

        // evidenceRef left null for admin suspend
        // Persist audit entry via EntityService (fire-and-wait to ensure audit appended)
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(Audit.ENTITY_NAME, Audit.ENTITY_VERSION, audit);
            UUID created = idFuture.get();
            logger.info("Created audit record for suspend_user: {}", created);
        } catch (Exception ex) {
            // Log error but continue; the user entity state is still updated locally and will be persisted by Cyoda
            logger.error("Failed to persist audit record for suspend_user: {}", ex.getMessage(), ex);
        }

        // 4. Append audit id to user's auditRefs (so user has a reference)
        List<String> auditRefs = user.getAuditRefs();
        if (auditRefs == null) {
            auditRefs = new ArrayList<>();
        }
        auditRefs.add(audit.getAuditId());
        user.setAuditRefs(auditRefs);

        // Return modified user; Cyoda will persist changes automatically as part of the workflow
        return user;
    }
}