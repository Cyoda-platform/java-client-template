package com.java_template.application.processor;

import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.application.entity.media.version_1.Media;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class PublishMedia implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PublishMedia.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PublishMedia(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Media for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Media.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Media entity) {
        return entity != null && entity.isValid();
    }

    private Media processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Media> context) {
        Media media = context.entity();

        try {
            // Business rule: When media is referenced by a published post, mark it as published.
            String currentStatus = media.getStatus();
            if (currentStatus == null || !currentStatus.equalsIgnoreCase("published")) {
                // If CDN ref is missing but versions exist, attempt to reuse first version filename as hint (non-destructive).
                if ((media.getCdn_ref() == null || media.getCdn_ref().isBlank()) && media.getVersions() != null && !media.getVersions().isEmpty()) {
                    // Do not invent a CDN reference; leave null if none exists. This is a no-op except status change.
                    logger.debug("Media {} has no cdn_ref; leaving cdn_ref unchanged while publishing.", media.getMedia_id());
                }

                media.setStatus("published");
                logger.info("Media {} status set to published", media.getMedia_id());

                // Append an audit entry for the publish action.
                Audit audit = new Audit();
                audit.setAuditId(UUID.randomUUID().toString());
                audit.setAction("publish_media");
                // Actor is system because this action is triggered by a post publish referencing this media.
                audit.setActorId("system");
                audit.setEntityRef(media.getMedia_id() + ":Media");
                audit.setTimestamp(Instant.now().toString());
                audit.setEvidenceRef(media.getCdn_ref()); // may be null
                audit.setMetadata(Map.of(
                    "filename", media.getFilename() != null ? media.getFilename() : "",
                    "owner_id", media.getOwner_id() != null ? media.getOwner_id() : "",
                    "previous_status", currentStatus != null ? currentStatus : ""
                ));

                try {
                    CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                        Audit.ENTITY_NAME,
                        Audit.ENTITY_VERSION,
                        audit
                    );
                    java.util.UUID auditId = idFuture.get();
                    logger.info("Appended audit {} for media {}", auditId, media.getMedia_id());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted while creating audit for media {}: {}", media.getMedia_id(), ie.getMessage(), ie);
                } catch (ExecutionException ee) {
                    logger.error("Failed to persist audit for media {}: {}", media.getMedia_id(), ee.getMessage(), ee);
                }
            } else {
                logger.debug("Media {} already published; no action taken.", media.getMedia_id());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error while publishing media {}: {}", media != null ? media.getMedia_id() : "unknown", ex.getMessage(), ex);
            // Do not throw; return entity as-is. Cyoda will persist entity state.
        }

        return media;
    }
}