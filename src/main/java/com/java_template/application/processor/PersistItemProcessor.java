package com.java_template.application.processor;

import com.java_template.application.entity.ingestjob.version_1.IngestJob;
import com.java_template.application.entity.storeditem.version_1.StoredItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistItemProcessor(SerializerFactory serializerFactory,
                                EntityService entityService,
                                ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestJob entity) {
        return entity != null && entity.isValid();
    }

    private IngestJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestJob> context) {
        IngestJob job = context.entity();
        try {
            // Serialize hnPayload to JSON string for storage
            String hnJson = objectMapper.writeValueAsString(job.getHnPayload());

            // Build StoredItem
            StoredItem stored = new StoredItem();
            stored.setHnItem(hnJson);
            stored.setSizeBytes(hnJson.getBytes(StandardCharsets.UTF_8).length);
            stored.setStoredAt(OffsetDateTime.now(ZoneOffset.UTC).toString());

            // Persist StoredItem (other-entity add)
            CompletableFuture<UUID> idFuture = entityService.addItem(
                StoredItem.ENTITY_NAME,
                String.valueOf(StoredItem.ENTITY_VERSION),
                stored
            );

            // Wait for creation to complete to obtain technical id
            UUID createdId = idFuture.join();
            String storageTechnicalId = createdId.toString();

            // Update stored entity to include the storageTechnicalId
            stored.setStorageTechnicalId(storageTechnicalId);
            CompletableFuture<UUID> updateFuture = entityService.updateItem(
                StoredItem.ENTITY_NAME,
                String.valueOf(StoredItem.ENTITY_VERSION),
                createdId,
                stored
            );
            // Wait for update to finish (optional but ensures storageTechnicalId is persisted)
            updateFuture.join();

            // Update the originating job entity fields (this entity will be persisted by Cyoda)
            job.setStoredItemTechnicalId(storageTechnicalId);
            job.setStatus("COMPLETED");
            job.setErrorMessage(null);

            logger.info("Successfully persisted StoredItem with id {} for job {}", storageTechnicalId, job.getTechnicalId());
        } catch (Exception e) {
            logger.error("Failed to persist StoredItem for job {}: {}", job != null ? job.getTechnicalId() : "unknown", e.getMessage(), e);
            if (job != null) {
                job.setStatus("FAILED");
                job.setErrorMessage(e.getMessage() != null ? e.getMessage() : "persisting stored item failed");
            }
        }

        return job;
    }
}