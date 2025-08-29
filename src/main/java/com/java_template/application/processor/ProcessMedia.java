package com.java_template.application.processor;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProcessMedia implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessMedia.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ProcessMedia(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Media for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Media.class)
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

    private boolean isValidEntity(Media entity) {
        return entity != null && entity.isValid();
    }

    private Media processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Media> context) {
        Media entity = context.entity();
        if (entity == null) {
            logger.warn("ProcessMedia invoked with null entity in context");
            return null;
        }

        // Business logic for process_media (ASYNC_JOB)
        // 1. Derive thumbnails/formats (simulate by creating versions entries)
        // 2. Upload to immutable storage (simulate by creating a cdn_ref)
        // 3. Set media.status = processed
        // 4. Append an Audit record describing the processing
        // 5. Do NOT call update/add/delete on this Media entity (Cyoda will persist the mutated entity automatically)
        try {
            // 1) Derive simple versions list if not present - simulate thumbnail/version generation
            List<Media.MediaVersion> versions = entity.getVersions();
            if (versions == null) {
                versions = new ArrayList<>();
            }

            // create a derived thumbnail version entry
            Media.MediaVersion thumb = new Media.MediaVersion();
            String thumbVersionId = UUID.randomUUID().toString();
            thumb.setVersion_id(thumbVersionId);
            // name thumbnail file with predictable pattern
            String thumbFilename = "thumb_" + (entity.getFilename() != null ? entity.getFilename() : "file");
            thumb.setFilename(thumbFilename);
            versions.add(thumb);

            // create a web-optimized version entry
            Media.MediaVersion web = new Media.MediaVersion();
            String webVersionId = UUID.randomUUID().toString();
            web.setVersion_id(webVersionId);
            String webFilename = "webopt_" + (entity.getFilename() != null ? entity.getFilename() : "file");
            web.setFilename(webFilename);
            versions.add(web);

            entity.setVersions(versions);

            // 2) Simulate uploading to immutable storage by generating a cdn_ref
            String generatedCdnRef = "cdn://media/" + (entity.getMedia_id() != null ? entity.getMedia_id() : UUID.randomUUID().toString());
            entity.setCdn_ref(generatedCdnRef);

            // 3) Update status to processed
            entity.setStatus("processed");

            // 4) Append an Audit record for this processing event using EntityService ADD operation
            Audit audit = new Audit();
            audit.setAuditId(UUID.randomUUID().toString());
            audit.setAction("process_media");
            // actorId = system for automated background processing
            audit.setActorId("system");
            String entityRef = (entity.getMedia_id() != null ? entity.getMedia_id() : "") + ":Media";
            audit.setEntityRef(entityRef);
            audit.setTimestamp(Instant.now().toString());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("cdn_ref", generatedCdnRef);
            metadata.put("derived_versions", List.of(thumbVersionId, webVersionId));
            metadata.put("filename", entity.getFilename());
            metadata.put("mime", entity.getMime());
            audit.setMetadata(metadata);

            // persist audit record asynchronously and wait for completion
            try {
                CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Audit.ENTITY_NAME,
                    Audit.ENTITY_VERSION,
                    audit
                );
                // block to ensure audit persisted; handle exceptions separately
                java.util.UUID auditId = idFuture.get();
                logger.info("Appended audit {} for media {}", auditId, entity.getMedia_id());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while persisting audit for media {}: {}", entity.getMedia_id(), ie.getMessage(), ie);
            } catch (ExecutionException ee) {
                logger.error("Failed to persist audit for media {}: {}", entity.getMedia_id(), ee.getMessage(), ee);
            } catch (Exception e) {
                logger.error("Unexpected error while persisting audit for media {}: {}", entity.getMedia_id(), e.getMessage(), e);
            }

        } catch (Exception ex) {
            logger.error("Error processing media {}: {}", entity.getMedia_id(), ex.getMessage(), ex);
            // In case of error, mark as failed but still return the entity so Cyoda can persist the state if desired
            try {
                entity.setStatus("processing_failed");
            } catch (Exception ignore) {
            }
        }

        return entity;
    }
}