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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class ArchivePost implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchivePost.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ArchivePost(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        Post entity = context.entity();

        // Business logic: allow archiving only when post is currently published.
        // If published -> set status to "archived" and append an Audit record.
        try {
            String currentStatus = entity.getStatus();
            if (currentStatus != null && "published".equalsIgnoreCase(currentStatus)) {
                // Update post status to archived
                entity.setStatus("archived");

                // Create audit entry for the archive action
                Audit audit = new Audit();
                audit.setAuditId(UUID.randomUUID().toString());
                audit.setAction("archive");
                // Actor for admin action in this processor: using "system" as actor identifier
                audit.setActorId("system");
                // Entity reference: use format "id:Post"
                String entityRef = (entity.getId() != null ? entity.getId() : "") + ":Post";
                audit.setEntityRef(entityRef);
                audit.setTimestamp(Instant.now().toString());

                // Persist audit via EntityService
                try {
                    CompletableFuture<UUID> idFuture = entityService.addItem(
                        Audit.ENTITY_NAME,
                        Audit.ENTITY_VERSION,
                        audit
                    );
                    // Wait for completion to ensure audit persisted
                    idFuture.get();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted while persisting audit for post {}: {}", entity.getId(), ie.getMessage(), ie);
                } catch (ExecutionException ee) {
                    logger.error("Failed to persist audit for post {}: {}", entity.getId(), ee.getMessage(), ee);
                } catch (Exception ex) {
                    logger.error("Unexpected error persisting audit for post {}: {}", entity.getId(), ex.getMessage(), ex);
                }
            } else {
                // Not published - nothing to do, just log
                logger.info("ArchivePost invoked but post {} is not in 'published' state (current: {}). No changes applied.", entity.getId(), currentStatus);
            }
        } catch (Exception ex) {
            logger.error("Unhandled error in ArchivePost processor for post {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
            // Do not throw; return entity unchanged so workflow can decide next steps.
        }

        return entity;
    }
}