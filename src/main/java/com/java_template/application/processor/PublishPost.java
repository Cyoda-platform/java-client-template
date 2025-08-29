package com.java_template.application.processor;

import com.java_template.application.entity.post.version_1.Post;
import com.java_template.application.entity.postversion.version_1.PostVersion;
import com.java_template.application.entity.media.version_1.Media;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class PublishPost implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PublishPost.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PublishPost(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Business logic for publishing a Post:
        // - Create immutable content bundle for the referenced PostVersion
        // - Upload bundle metadata (represented here by creating a Media entry and assigning cdn_ref)
        // - Set post.published_at = now, set status = "published", set cache_control
        // - Append an Audit entry for the publish action
        // - Trigger finalize/embeddings for the PostVersion by updating it (setting embeddings_ref / normalized_text)
        // Notes:
        // - Do not call update on the triggering Post entity; Cyoda will persist the changed Post automatically.
        // - We may add/update other entities via EntityService.

        try {
            String currentVersionId = post.getCurrent_version_id();
            // Create published timestamp
            String nowIso = Instant.now().toString();

            // If there is a referenced PostVersion, attempt to load it and finalize it
            if (currentVersionId != null && !currentVersionId.isBlank()) {
                try {
                    CompletableFuture<DataPayload> pvFuture = entityService.getItem(UUID.fromString(currentVersionId));
                    DataPayload pvPayload = pvFuture.get();
                    if (pvPayload != null && pvPayload.getData() != null) {
                        PostVersion pv = objectMapper.treeToValue(pvPayload.getData(), PostVersion.class);

                        // Normalize content_rich into normalized_text if missing
                        if ((pv.getNormalized_text() == null || pv.getNormalized_text().isBlank()) && pv.getContent_rich() != null) {
                            // Very simple normalization: strip HTML tags
                            String normalized = pv.getContent_rich().replaceAll("<[^>]*>", "").trim();
                            pv.setNormalized_text(normalized);
                        }

                        // Mark embeddings_ref to indicate finalization has been requested/started
                        String embeddingsRef = "embeddings-ref:" + UUID.randomUUID().toString();
                        pv.setEmbeddings_ref(embeddingsRef);

                        // Update PostVersion to trigger downstream processors (finalize / enqueue embeddings)
                        try {
                            CompletableFuture<UUID> updated = entityService.updateItem(UUID.fromString(pv.getVersion_id()), pv);
                            updated.get(); // wait for completion
                            logger.info("Requested finalize/embeddings for PostVersion {}", pv.getVersion_id());
                        } catch (InterruptedException | ExecutionException e) {
                            logger.error("Failed to update PostVersion {}: {}", pv.getVersion_id(), e.getMessage(), e);
                        }
                    } else {
                        logger.warn("PostVersion payload was null for id {}", currentVersionId);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    logger.error("Failed to fetch PostVersion {}: {}", currentVersionId, ex.getMessage(), ex);
                } catch (IllegalArgumentException iae) {
                    logger.error("Invalid PostVersion id format: {}", currentVersionId);
                }
            } else {
                logger.warn("Post {} has no current_version_id; publishing will proceed without finalizing a version.", post.getId());
            }

            // Create an immutable content bundle as a Media entity (represents uploaded bundle metadata)
            Media bundle = new Media();
            String mediaId = UUID.randomUUID().toString();
            bundle.setMedia_id(mediaId);
            // Use author as owner if present, otherwise fallback to owner_id
            String ownerRef = post.getAuthor_id() != null && !post.getAuthor_id().isBlank() ? post.getAuthor_id() : post.getOwner_id();
            bundle.setOwner_id(ownerRef);
            String filename = (post.getSlug() != null && !post.getSlug().isBlank()) ? post.getSlug() + "-bundle.json" : mediaId + "-bundle.json";
            bundle.setFilename(filename);
            bundle.setMime("application/json");
            String cdnRef = "cdn://bundle/" + UUID.randomUUID().toString();
            bundle.setCdn_ref(cdnRef);
            bundle.setCreated_at(nowIso);
            bundle.setStatus("processed");

            try {
                CompletableFuture<UUID> mediaAdd = entityService.addItem(Media.ENTITY_NAME, Media.ENTITY_VERSION, bundle);
                mediaAdd.get();
                logger.info("Created bundle Media {} for Post {}", mediaId, post.getId());

                // Add media reference to post.media_refs
                List<String> mediaRefs = post.getMedia_refs();
                if (mediaRefs == null) {
                    mediaRefs = new ArrayList<>();
                    post.setMedia_refs(mediaRefs);
                }
                mediaRefs.add(mediaId);
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Failed to add bundle Media for Post {}: {}", post.getId(), e.getMessage(), e);
            }

            // Set cache control and published timestamp/status on the Post entity (will be persisted by Cyoda)
            post.setCache_control("public, max-age=3600");
            post.setPublished_at(nowIso);
            post.setStatus("published");

            // Append an Audit entry for the publish action
            Audit audit = new Audit();
            audit.setAuditId(UUID.randomUUID().toString());
            audit.setAction("publish");
            // Actor: business rule states "Admin" for publish; use "Admin" literal
            audit.setActorId("Admin");
            audit.setEntityRef(post.getId() + ":Post");
            audit.setEvidenceRef(null);
            audit.setTimestamp(nowIso);
            // metadata: include who authored and version referenced
            java.util.Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("author_id", post.getAuthor_id());
            metadata.put("current_version_id", post.getCurrent_version_id());
            audit.setMetadata(metadata);

            try {
                CompletableFuture<UUID> auditAdd = entityService.addItem(Audit.ENTITY_NAME, Audit.ENTITY_VERSION, audit);
                auditAdd.get();
                logger.info("Appended audit for publish action for Post {}", post.getId());
                // Optionally add audit ref to Post if Post had a field for audit refs (it doesn't), so we skip modifying Post for audit refs.
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Failed to add Audit for Post {}: {}", post.getId(), e.getMessage(), e);
            }

        } catch (Exception e) {
            // Catch-all to prevent processor crash; log and return entity unchanged (Cyoda will mark errors appropriately)
            logger.error("Unexpected error while publishing post {}: {}", post.getId(), e.getMessage(), e);
        }

        return post;
    }
}