package com.java_template.application.processor;

import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.application.entity.media.version_1.Media;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DeprecateMedia implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeprecateMedia.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public DeprecateMedia(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Media for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Media.class)
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
            logger.warn("Media entity is null in execution context.");
            return entity;
        }

        String currentStatus = entity.getStatus();
        if (currentStatus != null && "deprecated".equalsIgnoreCase(currentStatus)) {
            logger.info("Media {} is already deprecated. No action taken.", entity.getMedia_id());
            return entity;
        }

        // Business rule: mark media as deprecated when admin triggers deprecation.
        entity.setStatus("deprecated");

        // Append an Audit record for this guarded transition using EntityService (allowed to add other entities)
        try {
            Audit audit = new Audit();
            audit.setAudit_id(UUID.randomUUID().toString());
            audit.setAction("deprecate_media");
            audit.setActor_id("system"); // actor information not available in context reliably; use system/admin placeholder
            String mediaRef = entity.getMedia_id() != null ? entity.getMedia_id() + ":Media" : "unknown:Media";
            audit.setEntity_ref(mediaRef);
            audit.setTimestamp(Instant.now().toString());
            audit.setMetadata(Map.of("previousStatus", currentStatus == null ? "unknown" : currentStatus,
                                     "filename", entity.getFilename() == null ? "" : entity.getFilename()));

            CompletableFuture<UUID> idFuture = entityService.addItem(
                Audit.ENTITY_NAME,
                String.valueOf(Audit.ENTITY_VERSION),
                audit
            );

            idFuture.whenComplete((id, ex) -> {
                if (ex != null) {
                    logger.error("Failed to add Audit for media deprecation (media_id={}): {}", entity.getMedia_id(), ex.getMessage());
                } else {
                    logger.info("Appended Audit {} for media deprecation (media_id={})", id, entity.getMedia_id());
                }
            });
        } catch (Exception ex) {
            logger.error("Exception while creating audit for media deprecation (media_id={}): {}", entity.getMedia_id(), ex.getMessage());
        }

        // Important: Do not call entityService.updateItem for this entity. Cyoda will persist the modified entity automatically.
        return entity;
    }
}