package com.java_template.application.processor;

import com.java_template.application.entity.media.version_1.Media;
import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ProcessMedia implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessMedia.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ProcessMedia(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Media for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Media.class)
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

        // Business logic for processing media:
        // - If media already processed or in a non-uploaded state, skip processing.
        // - Otherwise derive CDN reference, mark as processed, and append an Audit record.
        try {
            String status = media.getStatus();
            if (status != null && status.equalsIgnoreCase("processed")) {
                logger.info("Media {} already processed, skipping.", media.getMedia_id());
                return media;
            }

            // Only process when in uploaded state (or when status is null treat as uploaded)
            if (status != null && !status.equalsIgnoreCase("uploaded") && !status.isBlank()) {
                logger.info("Media {} in state '{}' - not eligible for processing by ProcessMedia.", media.getMedia_id(), status);
                return media;
            }

            // Simulate deriving thumbnails/formats and uploading to CDN by creating a canonical CDN ref.
            // Do not call external services here; set a deterministic CDN reference so it can be persisted.
            String currentCdnRef = media.getCdn_ref();
            if (currentCdnRef == null || currentCdnRef.isBlank()) {
                String generated = String.format("cdn://media/%s", media.getMedia_id() != null ? media.getMedia_id() : UUID.randomUUID().toString());
                media.setCdn_ref(generated);
                logger.info("Set cdn_ref for media {} -> {}", media.getMedia_id(), generated);
            }

            // Mark as processed
            media.setStatus("processed");

            // Append audit entry for processing action (write as a separate entity)
            Audit audit = new Audit();
            audit.setAudit_id(UUID.randomUUID().toString());
            audit.setAction("process_media");
            audit.setActor_id("system");
            String entityRef = (media.getMedia_id() != null ? media.getMedia_id() : "") + ":Media";
            audit.setEntity_ref(entityRef);
            audit.setTimestamp(Instant.now().toString());
            audit.setEvidence_ref(null);
            audit.setMetadata(null);

            // Add audit asynchronously; do not block processing. Log result/failure.
            try {
                CompletableFuture<java.util.UUID> addFuture = entityService.addItem(
                    Audit.ENTITY_NAME,
                    String.valueOf(Audit.ENTITY_VERSION),
                    audit
                );
                addFuture.whenComplete((id, ex) -> {
                    if (ex != null) {
                        logger.error("Failed to persist audit for media {}: {}", media.getMedia_id(), ex.getMessage(), ex);
                    } else {
                        logger.info("Persisted audit {} for media {}", id, media.getMedia_id());
                    }
                });
            } catch (Exception ex) {
                logger.error("Exception while scheduling audit persistence for media {}: {}", media.getMedia_id(), ex.getMessage(), ex);
            }

        } catch (Exception ex) {
            logger.error("Unexpected error processing media {}: {}", media != null ? media.getMedia_id() : "unknown", ex.getMessage(), ex);
        }

        return media;
    }
}