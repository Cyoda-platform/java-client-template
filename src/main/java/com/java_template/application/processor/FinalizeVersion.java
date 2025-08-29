package com.java_template.application.processor;

import com.java_template.application.entity.postversion.version_1.PostVersion;
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

@Component
public class FinalizeVersion implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FinalizeVersion.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public FinalizeVersion(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PostVersion for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PostVersion.class)
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

    private boolean isValidEntity(PostVersion entity) {
        return entity != null && entity.isValid();
    }

    private PostVersion processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PostVersion> context) {
        PostVersion entity = context.entity();

        // Ensure normalized_text exists: simple normalization (strip basic HTML tags) if missing
        try {
            String content = entity.getContent_rich();
            String normalized = entity.getNormalized_text();

            if ((normalized == null || normalized.isBlank()) && content != null) {
                // very simple normalization: remove tags and trim
                String stripped = content.replaceAll("<[^>]*>", " ");
                stripped = stripped.replaceAll("\\s+", " ").trim();
                entity.setNormalized_text(stripped);
                normalized = stripped;
                logger.info("PostVersion {} normalized_text computed (length={})", entity.getVersion_id(), stripped.length());
            }

            // If chunks_meta missing or empty, create simple chunks from normalized_text
            List<PostVersion.ChunkMeta> chunks = entity.getChunks_meta();
            if ((chunks == null || chunks.isEmpty()) && entity.getNormalized_text() != null) {
                String text = entity.getNormalized_text();
                int maxChunkSize = 1000;
                List<PostVersion.ChunkMeta> newChunks = new ArrayList<>();
                int start = 0;
                while (start < text.length()) {
                    int end = Math.min(start + maxChunkSize, text.length());
                    String part = text.substring(start, end);
                    PostVersion.ChunkMeta cm = new PostVersion.ChunkMeta();
                    cm.setChunk_ref(UUID.randomUUID().toString());
                    cm.setText(part);
                    newChunks.add(cm);
                    start = end;
                }
                entity.setChunks_meta(newChunks);
                logger.info("PostVersion {} chunks_meta generated (chunks={})", entity.getVersion_id(), newChunks.size());
            }

            // Set embeddings_ref to indicate embeddings should be enqueued/processed.
            if (entity.getEmbeddings_ref() == null || entity.getEmbeddings_ref().isBlank()) {
                String embeddingsRef = "embeddings:queued:" + UUID.randomUUID().toString();
                entity.setEmbeddings_ref(embeddingsRef);
                logger.info("PostVersion {} embeddings_ref set to {}", entity.getVersion_id(), embeddingsRef);
            }

            // Append an Audit record for this finalized version
            try {
                Audit audit = new Audit();
                audit.setAuditId(UUID.randomUUID().toString());
                audit.setAction("finalize_version");
                String actor = entity.getAuthor_id() != null && !entity.getAuthor_id().isBlank() ? entity.getAuthor_id() : "system";
                audit.setActorId(actor);
                String entityRef = entity.getVersion_id() + ":PostVersion";
                audit.setEntityRef(entityRef);
                audit.setTimestamp(Instant.now().toString());
                // minimal metadata
                // persist audit (add other entity)
                CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Audit.ENTITY_NAME,
                    Audit.ENTITY_VERSION,
                    audit
                );
                try {
                    java.util.UUID addedId = idFuture.get();
                    logger.info("Audit created for PostVersion {} with technical id {}", entity.getVersion_id(), addedId);
                } catch (Exception e) {
                    logger.error("Failed to persist Audit for PostVersion {}: {}", entity.getVersion_id(), e.getMessage(), e);
                }
            } catch (Exception e) {
                logger.error("Error while creating audit for PostVersion {}: {}", entity.getVersion_id(), e.getMessage(), e);
            }

        } catch (Exception ex) {
            logger.error("Error finalizing PostVersion {}: {}", entity != null ? entity.getVersion_id() : "unknown", ex.getMessage(), ex);
        }

        return entity;
    }
}