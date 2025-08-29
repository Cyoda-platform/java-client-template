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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DeprecateMedia implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeprecateMedia.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DeprecateMedia(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Business logic for deprecating media:
        // - Set media status to "deprecated"
        // - Append an Audit record describing the deprecation
        // - Do NOT perform add/update/delete operations on the triggering Media entity via EntityService;
        //   simply mutate the entity object. Cyoda will persist it automatically.
        try {
            // Set status to deprecated
            entity.setStatus("deprecated");

            // Create audit record for the deprecation action
            Audit audit = new Audit();
            audit.setAuditId(UUID.randomUUID().toString());
            audit.setAction("deprecate_media");
            // Actor is not provided by the context reliably; use "system" as default for automated/admin actions
            audit.setActorId("system");
            String mediaRef = (entity.getMedia_id() != null ? entity.getMedia_id() : "") + ":" + Media.ENTITY_NAME;
            audit.setEntityRef(mediaRef);
            audit.setTimestamp(Instant.now().toString());
            // include some metadata to help trace the deprecation
            Map<String, Object> metadata = Map.of(
                "filename", entity.getFilename() != null ? entity.getFilename() : "",
                "owner_id", entity.getOwner_id() != null ? entity.getOwner_id() : ""
            );
            audit.setMetadata(metadata);

            // Persist the audit record as a separate entity
            // Use EntityService to add the audit; wait for completion but do not fail processor on audit persistence errors.
            try {
                CompletableFuture<UUID> idFuture = entityService.addItem(
                    Audit.ENTITY_NAME,
                    Audit.ENTITY_VERSION,
                    audit
                );
                // block to ensure audit persisted (best-effort). If this throws, we'll log and continue.
                idFuture.get();
            } catch (Exception ex) {
                logger.error("Failed to persist audit for media deprecation (media_id={}): {}", entity.getMedia_id(), ex.getMessage(), ex);
            }

        } catch (Exception ex) {
            // Ensure any unexpected exception is logged; return entity as-is (Cyoda will persist mutated state).
            logger.error("Error while processing deprecate logic for media (media_id={}): {}", entity != null ? entity.getMedia_id() : "null", ex.getMessage(), ex);
        }

        return entity;
    }
}