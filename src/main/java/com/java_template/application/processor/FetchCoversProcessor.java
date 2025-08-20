package com.java_template.application.processor;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.coverphoto.version_1.CoverPhoto;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class FetchCoversProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchCoversProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FetchCoversProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestionJob FetchCovers for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        return entity != null;
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();
        try {
            job.setStartedAt(Instant.now());
            job.setStatus("RUNNING");

            // For prototype: fetch a small fixed list or derive from runParameters.source
            List<ObjectNode> fetched = new ArrayList<>();
            // simulate two items
            ObjectNode item1 = objectMapper.createObjectNode();
            item1.put("coverId", "o-1");
            item1.put("title", "Example Cover 1");
            item1.put("imageUrl", "https://example.com/image1.jpg");
            fetched.add(item1);

            ObjectNode item2 = objectMapper.createObjectNode();
            item2.put("coverId", "o-2");
            item2.put("title", "Example Cover 2");
            item2.put("imageUrl", "https://example.com/image2.jpg");
            fetched.add(item2);

            job.setFetchedCount(fetched.size());
            logger.info("Fetched {} items for job {}", fetched.size(), job.getTechnicalId());

            // Persist each item by delegating to entityService addItem (PersistCoversProcessor handles actual persistence)
            for (ObjectNode item : fetched) {
                // build minimal CoverPhoto object
                CoverPhoto cp = new CoverPhoto();
                cp.setCoverId(item.has("coverId") ? item.get("coverId").asText() : null);
                cp.setTitle(item.has("title") ? item.get("title").asText() : null);
                cp.setImageUrl(item.has("imageUrl") ? item.get("imageUrl").asText() : null);
                cp.setStatus("RECEIVED");
                cp.setIngestionJobId(job.getTechnicalId());
                cp.setFetchedAt(Instant.now());

                try {
                    CompletableFuture<UUID> idFuture = entityService.addItem(
                        CoverPhoto.ENTITY_NAME,
                        String.valueOf(CoverPhoto.ENTITY_VERSION),
                        cp
                    );
                    // Not blocking: best effort
                    idFuture.thenAccept(id -> logger.info("Persisted CoverPhoto with id {} from job {}", id, job.getTechnicalId()));
                } catch (Exception e) {
                    logger.warn("Failed to add cover photo item to entity service: {}", e.getMessage());
                    job.setErrorCount((job.getErrorCount() == null ? 0 : job.getErrorCount()) + 1);
                }
            }

            return job;
        } catch (Exception ex) {
            logger.error("Unexpected error in FetchCoversProcessor for {}: {}", job == null ? "?" : job.getTechnicalId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            return job;
        }
    }
}
