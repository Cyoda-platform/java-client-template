package com.java_template.application.processor;

import com.java_template.application.entity.postversion.version_1.PostVersion;
import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class EnqueueEmbeddings implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnqueueEmbeddings.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public EnqueueEmbeddings(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PostVersion for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PostVersion.class)
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

        logger.info("Enqueueing embeddings for PostVersion: {}", entity.getVersionId());

        // Business logic:
        // - For the provided PostVersion, enqueue embeddings for each chunk (logical enqueue).
        // - Produce a single embeddingsRef for the version and attach it to the entity.
        // - Append an Audit entry recording the enqueue operation (stored as separate entity).
        try {
            int chunkCount = (entity.getChunksMeta() == null) ? 0 : entity.getChunksMeta().size();

            // Generate a store reference for embeddings for this version (represents the vector store ref)
            String embeddingsStoreRef = "embeddings://" + UUID.randomUUID().toString();
            entity.setEmbeddingsRef(embeddingsStoreRef);

            // Create an audit record describing the enqueue operation
            Audit audit = new Audit();
            audit.setAudit_id(UUID.randomUUID().toString());
            audit.setAction("enqueue_embeddings");
            // actor_id is required; use a system actor marker. It's a non-blank identifier.
            audit.setActor_id("system");
            audit.setEntity_ref(entity.getVersionId() + ":PostVersion");
            audit.setTimestamp(Instant.now().toString());
            audit.setEvidence_ref(null);
            audit.setMetadata(Map.of(
                "post_id", entity.getPostId(),
                "version_id", entity.getVersionId(),
                "chunk_count", chunkCount,
                "embeddings_ref", embeddingsStoreRef
            ));

            // Persist audit asynchronously (we are allowed to add other entities)
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                Audit.ENTITY_NAME,
                String.valueOf(Audit.ENTITY_VERSION),
                audit
            );

            idFuture.whenComplete((id, ex) -> {
                if (ex != null) {
                    logger.error("Failed to persist audit for enqueue_embeddings (version={}): {}", entity.getVersionId(), ex.getMessage());
                } else {
                    logger.info("Audit persisted for enqueue_embeddings (version={}, auditTechnicalId={})", entity.getVersionId(), id);
                }
            });

            // Note: Actual embedding computation/queueing to external vector service is handled by downstream workers.
            // Here we only create the logical reference and record the enqueue action.

        } catch (Exception e) {
            logger.error("Error while enqueuing embeddings for PostVersion {}: {}", entity.getVersionId(), e.getMessage(), e);
            // Do not throw; allow workflow to persist the entity state (with whatever was set).
        }

        return entity;
    }
}