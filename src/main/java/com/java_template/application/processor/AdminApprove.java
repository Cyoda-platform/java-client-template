package com.java_template.application.processor;

import com.java_template.application.entity.post.version_1.Post;
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
import java.util.concurrent.CompletableFuture;

@Component
public class AdminApprove implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdminApprove.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AdminApprove(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Post for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Post.class)
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

    private boolean isValidEntity(Post entity) {
        return entity != null && entity.isValid();
    }

    private Post processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Post> context) {
        Post post = context.entity();

        // Business rules for Admin Approve:
        // - Only process if post is currently in "in_review"
        // - If publish_datetime is present and in the future -> set status "scheduled"
        // - If publish_datetime is missing or is <= now -> set status "published" and set published_at to now
        // - Append an Audit record for the approval action (and for scheduled/published outcome)
        if (post == null) {
            logger.warn("Post entity is null in AdminApprove processor");
            return post;
        }

        String currentStatus = post.getStatus();
        if (currentStatus == null || !currentStatus.equalsIgnoreCase("in_review")) {
            logger.info("Post {} is not in 'in_review' state (current:{}). Skipping admin approve.", post.getId(), currentStatus);
            return post;
        }

        String publishDatetime = post.getPublish_datetime();
        Instant now = Instant.now();

        boolean willPublishNow = true;
        if (publishDatetime != null && !publishDatetime.isBlank()) {
            try {
                Instant publishInstant = Instant.parse(publishDatetime);
                if (publishInstant.isAfter(now)) {
                    // schedule for future
                    post.setStatus("scheduled");
                    // Do not set published_at yet
                    willPublishNow = false;
                    logger.info("Post {} scheduled for publish at {}", post.getId(), publishDatetime);

                    // Audit entry for scheduling
                    createAuditRecord(post, "admin_approve_scheduled", "Admin", Map.of(
                        "previousStatus", currentStatus,
                        "publish_datetime", publishDatetime
                    ));
                } else {
                    // publish immediately (publishDatetime <= now)
                    post.setStatus("published");
                    post.setPublished_at(now.toString());
                    // Set a default cache_control if not present
                    if (post.getCache_control() == null || post.getCache_control().isBlank()) {
                        post.setCache_control("public, max-age=3600");
                    }
                    logger.info("Post {} approved and published at {}", post.getId(), post.getPublished_at());

                    // Audit entry for publish via admin approve
                    createAuditRecord(post, "admin_approve_published", "Admin", Map.of(
                        "previousStatus", currentStatus,
                        "publish_datetime", publishDatetime == null ? "" : publishDatetime
                    ));
                }
            } catch (Exception ex) {
                // If parsing fails, treat as immediate publish (safer fallback) but log the error
                logger.warn("Failed to parse publish_datetime '{}' for post {}: {}. Publishing immediately.", publishDatetime, post.getId(), ex.getMessage());
                post.setStatus("published");
                post.setPublished_at(now.toString());
                if (post.getCache_control() == null || post.getCache_control().isBlank()) {
                    post.setCache_control("public, max-age=3600");
                }
                createAuditRecord(post, "admin_approve_published", "Admin", Map.of(
                    "previousStatus", currentStatus,
                    "publish_datetime", publishDatetime == null ? "" : publishDatetime
                ));
            }
        } else {
            // No publish datetime -> publish immediately
            post.setStatus("published");
            post.setPublished_at(now.toString());
            if (post.getCache_control() == null || post.getCache_control().isBlank()) {
                post.setCache_control("public, max-age=3600");
            }
            logger.info("Post {} approved and published at {} (no publish_datetime provided)", post.getId(), post.getPublished_at());

            createAuditRecord(post, "admin_approve_published", "Admin", Map.of(
                "previousStatus", currentStatus
            ));
        }

        // Note: The triggering Post entity will be persisted by the workflow runtime automatically.
        // We only create related entities (Audit) via EntityService as side-effects.
        return post;
    }

    private void createAuditRecord(Post post, String action, String actorId, Map<String, Object> metadata) {
        try {
            Audit audit = new Audit();
            audit.setAuditId(UUID.randomUUID().toString());
            audit.setAction(action);
            audit.setActorId(actorId == null ? "system" : actorId);
            String entityRef = post.getId() == null ? "" : post.getId() + ":Post";
            audit.setEntityRef(entityRef);
            audit.setTimestamp(Instant.now().toString());
            if (metadata != null) {
                audit.setMetadata(new HashMap<>(metadata));
            }

            CompletableFuture<java.util.UUID> future = entityService.addItem(
                Audit.ENTITY_NAME,
                Audit.ENTITY_VERSION,
                audit
            );
            // Ensure the add completed to surface errors early
            future.get();
            logger.info("Appended audit {} for post {}", audit.getAuditId(), post.getId());
        } catch (Exception e) {
            logger.error("Failed to create audit for post {}: {}", post.getId(), e.getMessage(), e);
        }
    }
}