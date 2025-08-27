package com.java_template.application.processor;

import com.java_template.application.entity.post.version_1.Post;
import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

@Component
public class PublishPost implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PublishPost.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public PublishPost(SerializerFactory serializerFactory, EntityService entityService) {
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
        return entity != null && entity.isValid();
    }

    private Post processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Post> context) {
        Post post = context.entity();

        // 1. Validate presence of a current version reference - publishing without a version is invalid
        if (post.getCurrentVersionId() == null || post.getCurrentVersionId().isBlank()) {
            logger.warn("PublishPost: post {} has no currentVersionId, aborting publish.", post.getId());
            return post;
        }

        Instant nowInstant = Instant.now();
        String nowIso = nowInstant.toString();

        // 2. If a publish_datetime exists in the future, mark scheduled and do not publish now.
        String publishDatetime = post.getPublishDatetime();
        if (publishDatetime != null && !publishDatetime.isBlank()) {
            try {
                Instant publishInstant = Instant.parse(publishDatetime);
                if (publishInstant.isAfter(nowInstant)) {
                    // The post is scheduled for future publishing
                    post.setStatus("scheduled");
                    // Ensure cache control has a sensible default even when scheduled
                    if (post.getCacheControl() == null || post.getCacheControl().isBlank()) {
                        post.setCacheControl("public, max-age=60, s-maxage=3600, stale-while-revalidate=86400");
                    }

                    // Append an audit entry indicating scheduling
                    try {
                        Audit scheduleAudit = new Audit();
                        scheduleAudit.setAudit_id(UUID.randomUUID().toString());
                        scheduleAudit.setAction("schedule_publish");
                        scheduleAudit.setActor_id("system");
                        scheduleAudit.setEntity_ref(post.getId() + ":Post");
                        scheduleAudit.setTimestamp(nowIso);
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("scheduledFor", publishDatetime);
                        metadata.put("currentVersionId", post.getCurrentVersionId());
                        scheduleAudit.setMetadata(metadata);

                        CompletableFuture<UUID> af = entityService.addItem(
                            Audit.ENTITY_NAME,
                            String.valueOf(Audit.ENTITY_VERSION),
                            scheduleAudit
                        );
                        af.whenComplete((id, ex) -> {
                            if (ex != null) {
                                logger.error("Failed to persist schedule_publish audit for post {}: {}", post.getId(), ex.getMessage());
                            } else {
                                logger.info("Persisted schedule_publish audit {} for post {}", id, post.getId());
                            }
                        });
                    } catch (Exception e) {
                        logger.error("Exception while creating schedule_publish audit for post {}: {}", post.getId(), e.getMessage());
                    }

                    logger.info("PublishPost: post {} scheduled for publish at {}, deferring actual publish.", post.getId(), publishDatetime);
                    return post;
                }
            } catch (DateTimeParseException dtpe) {
                logger.warn("PublishPost: post {} has invalid publishDatetime '{}', continuing to publish now. Error: {}", post.getId(), publishDatetime, dtpe.getMessage());
                // continue to publish
            } catch (Exception e) {
                logger.warn("PublishPost: unexpected error parsing publishDatetime for post {}: {}, continuing to publish now.", post.getId(), e.getMessage());
            }
        }

        // 3. Prepare publish metadata: set published timestamp and cache control, set status to published
        post.setPublishedAt(nowIso);
        post.setStatus("published");

        if (post.getCacheControl() == null || post.getCacheControl().isBlank()) {
            post.setCacheControl("public, max-age=60, s-maxage=3600, stale-while-revalidate=86400");
        }

        // 4. Append an Audit record for the publish action
        try {
            Audit publishAudit = new Audit();
            publishAudit.setAudit_id(UUID.randomUUID().toString());
            publishAudit.setAction("publish");
            // Actor per functional requirement: Admin performed approval/publish
            publishAudit.setActor_id("Admin");
            publishAudit.setEntity_ref(post.getId() + ":Post");
            publishAudit.setTimestamp(nowIso);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("currentVersionId", post.getCurrentVersionId());
            metadata.put("title", post.getTitle());
            publishAudit.setMetadata(metadata);

            CompletableFuture<UUID> auditFuture = entityService.addItem(
                Audit.ENTITY_NAME,
                String.valueOf(Audit.ENTITY_VERSION),
                publishAudit
            );
            auditFuture.whenComplete((id, ex) -> {
                if (ex != null) {
                    logger.error("Failed to persist publish audit for post {}: {}", post.getId(), ex.getMessage());
                } else {
                    logger.info("Persisted publish audit {} for post {}", id, post.getId());
                }
            });
        } catch (Exception e) {
            logger.error("Exception while creating publish audit for post {}: {}", post.getId(), e.getMessage());
        }

        // 5. Trigger finalization of the referenced PostVersion by appending an Audit for that action.
        //    This acts as a durable event record which can be observed by processors responsible for PostVersion finalization.
        try {
            Audit versionAudit = new Audit();
            versionAudit.setAudit_id(UUID.randomUUID().toString());
            versionAudit.setAction("finalize_version");
            versionAudit.setActor_id("system");
            versionAudit.setEntity_ref(post.getCurrentVersionId() + ":PostVersion");
            versionAudit.setTimestamp(nowIso);
            Map<String, Object> vmeta = new HashMap<>();
            vmeta.put("triggeredByPost", post.getId());
            vmeta.put("postStatus", post.getStatus());
            versionAudit.setMetadata(vmeta);

            CompletableFuture<UUID> vaFuture = entityService.addItem(
                Audit.ENTITY_NAME,
                String.valueOf(Audit.ENTITY_VERSION),
                versionAudit
            );
            vaFuture.whenComplete((id, ex) -> {
                if (ex != null) {
                    logger.error("Failed to persist finalize_version audit for version {}: {}", post.getCurrentVersionId(), ex.getMessage());
                } else {
                    logger.info("Persisted finalize_version audit {} for version {}", id, post.getCurrentVersionId());
                }
            });
        } catch (Exception e) {
            logger.error("Exception while creating finalize_version audit for version {}: {}", post.getCurrentVersionId(), e.getMessage());
        }

        // 6. For any referenced media, append an audit indicating they are referenced by a published post.
        if (post.getMediaRefs() != null && !post.getMediaRefs().isEmpty()) {
            for (String mediaRef : post.getMediaRefs()) {
                if (mediaRef == null || mediaRef.isBlank()) continue;
                try {
                    Audit mediaAudit = new Audit();
                    mediaAudit.setAudit_id(UUID.randomUUID().toString());
                    mediaAudit.setAction("referenced_by_published_post");
                    mediaAudit.setActor_id("system");
                    mediaAudit.setEntity_ref(mediaRef + ":Media");
                    mediaAudit.setTimestamp(nowIso);
                    Map<String, Object> mmeta = new HashMap<>();
                    mmeta.put("postId", post.getId());
                    mmeta.put("postStatus", post.getStatus());
                    mediaAudit.setMetadata(mmeta);

                    CompletableFuture<UUID> maFuture = entityService.addItem(
                        Audit.ENTITY_NAME,
                        String.valueOf(Audit.ENTITY_VERSION),
                        mediaAudit
                    );
                    maFuture.whenComplete((id, ex) -> {
                        if (ex != null) {
                            logger.error("Failed to persist media reference audit for media {}: {}", mediaRef, ex.getMessage());
                        } else {
                            logger.info("Persisted media reference audit {} for media {}", id, mediaRef);
                        }
                    });
                } catch (Exception e) {
                    logger.error("Exception while creating media reference audit for media {}: {}", mediaRef, e.getMessage());
                }
            }
        }

        // The post entity is modified in-memory (publishedAt, status, cacheControl).
        // Cyoda will persist the triggering entity automatically as part of the workflow.
        return post;
    }
}