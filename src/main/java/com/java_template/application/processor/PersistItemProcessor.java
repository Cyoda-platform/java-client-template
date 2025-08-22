package com.java_template.application.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.ingestjob.version_1.IngestJob;
import com.java_template.application.entity.storeditem.version_1.StoredItem;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
            // Build StoredItem from job.hnPayload
            StoredItem stored = new StoredItem();

            String hnJson;
            try {
                hnJson = objectMapper.writeValueAsString(job.getHnPayload());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize hn payload", e);
            }

            stored.setHnItem(hnJson);
            stored.setSizeBytes(hnJson.getBytes(StandardCharsets.UTF_8).length);
            stored.setStoredAt(Instant.now().toString());

            // Persist StoredItem (ADD)
            CompletableFuture<UUID> idFuture = entityService.addItem(
                StoredItem.ENTITY_NAME,
                String.valueOf(StoredItem.ENTITY_VERSION),
                stored
            );

            UUID storedUuid = idFuture.join();
            String storageTechnicalId = storedUuid.toString();

            // Update the StoredItem to include its storageTechnicalId (allowed: updating other entities)
            stored.setStorageTechnicalId(storageTechnicalId);
            entityService.updateItem(
                StoredItem.ENTITY_NAME,
                String.valueOf(StoredItem.ENTITY_VERSION),
                storedUuid,
                stored
            ).join();

            // Update the originating job state - it will be persisted automatically by Cyoda
            job.setStoredItemTechnicalId(storageTechnicalId);
            job.setStatus("COMPLETED");
            job.setErrorMessage(null);

            logger.info("Persisted StoredItem with id {} for job {}", storageTechnicalId, job.getTechnicalId());
        } catch (Exception e) {
            logger.error("Failed to persist StoredItem for job {}: {}", job != null ? job.getTechnicalId() : "unknown", e.getMessage(), e);
            if (job != null) {
                job.setStatus("FAILED");
                job.setErrorMessage(e.getMessage() != null ? e.getMessage() : "unknown error");
            }
        }

        return job;
    }
}