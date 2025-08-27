package com.java_template.application.processor;
import com.java_template.application.entity.post.version_1.Post;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class SubmitForReview implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubmitForReview.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SubmitForReview(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Post for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Post.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Post entity) {
        return entity != null && entity.isValid()
            && entity.getCurrentVersionId() != null && !entity.getCurrentVersionId().isBlank()
            && entity.getTitle() != null && !entity.getTitle().isBlank()
            && entity.getSlug() != null && !entity.getSlug().isBlank();
    }

    private Post processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Post> context) {
        Post entity = context.entity();

        // 1. Validate required fields again defensively
        if (entity.getTitle() == null || entity.getTitle().isBlank()) {
            logger.warn("Post missing title - aborting submit_for_review for id={}", entity.getId());
            return entity;
        }
        if (entity.getSlug() == null || entity.getSlug().isBlank()) {
            logger.warn("Post missing slug - aborting submit_for_review for id={}", entity.getId());
            return entity;
        }
        if (entity.getCurrentVersionId() == null || entity.getCurrentVersionId().isBlank()) {
            logger.warn("Post missing currentVersionId - aborting submit_for_review for id={}", entity.getId());
            return entity;
        }

        // 2. Normalize tags: trim, lowercase and remove blank entries
        List<String> tags = entity.getTags();
        if (tags != null) {
            List<String> normalized = tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
            entity.setTags(normalized);
        }

        // 3. Append audit entry for this transition
        try {
            Audit audit = new Audit();
            audit.setAudit_id(UUID.randomUUID().toString());
            audit.setAction("submit_for_review");
            // actor is the author initiating the submit
            audit.setActor_id(entity.getAuthorId());
            audit.setEntity_ref((entity.getId() != null ? entity.getId() : "") + ":Post");
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("previous_status", entity.getStatus());
            metadata.put("current_version_id", entity.getCurrentVersionId());
            audit.setMetadata(metadata);
            audit.setTimestamp(Instant.now().toString());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                Audit.ENTITY_NAME,
                String.valueOf(Audit.ENTITY_VERSION),
                audit
            );
            idFuture.whenComplete((uuid, ex) -> {
                if (ex != null) {
                    logger.error("Failed to add Audit for submit_for_review postId={} error={}", entity.getId(), ex.getMessage());
                } else {
                    logger.info("Appended Audit {} for postId={}", uuid, entity.getId());
                }
            });
        } catch (Exception e) {
            logger.error("Exception while creating audit for post={} : {}", entity.getId(), e.getMessage());
        }

        // 4. Transition post status to in_review
        entity.setStatus("in_review");

        // Note: Do NOT call entityService.updateItem on this Post entity.
        // Cyoda will persist changes to the triggering entity automatically.

        return entity;
    }
}