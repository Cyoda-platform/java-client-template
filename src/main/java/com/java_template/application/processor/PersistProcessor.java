package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.petsyncjob.version_1.PetSyncJob;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetSyncJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetSyncJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PetSyncJob entity) {
        return entity != null && entity.isValid();
    }

    private PetSyncJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetSyncJob> context) {
        PetSyncJob job = context.entity();

        // Mark that persisting started (update in-memory state; Cyoda will persist this entity)
        job.setStatus("PERSISTING");
        if (job.getStartTime() == null || job.getStartTime().isBlank()) {
            job.setStartTime(Instant.now().toString());
        }

        Map<String, Object> config = job.getConfig();
        if (config == null) {
            job.setStatus("FAILED");
            job.setErrorMessage("Missing job config");
            job.setEndTime(Instant.now().toString());
            return job;
        }

        Object itemsObj = config.get("items");
        if (itemsObj == null || !(itemsObj instanceof List)) {
            job.setStatus("FAILED");
            job.setErrorMessage("No items found in job config under key 'items'");
            job.setEndTime(Instant.now().toString());
            return job;
        }

        List<?> items = (List<?>) itemsObj;
        List<CompletableFuture<?>> futures = new ArrayList<>();
        int initialCount = job.getFetchedCount() == null ? 0 : job.getFetchedCount();

        for (Object item : items) {
            try {
                // Map the raw item to Pet using ObjectMapper
                Pet pet = objectMapper.convertValue(item, Pet.class);

                // Apply minimal persistence-time normalization/defaults (do not invent domain ids)
                if (pet.getStatus() == null || pet.getStatus().isBlank()) {
                    pet.setStatus("available");
                }

                // Ensure source metadata contains the job source
                Pet.SourceMetadata sm = pet.getSourceMetadata();
                if (sm == null) {
                    sm = new Pet.SourceMetadata();
                    pet.setSourceMetadata(sm);
                }
                if (sm.getSource() == null || sm.getSource().isBlank()) {
                    sm.setSource(job.getSource());
                }
                if (sm.getExternalId() == null || sm.getExternalId().isBlank()) {
                    // Prefer pet.id when available
                    if (pet.getId() != null && !pet.getId().isBlank()) {
                        sm.setExternalId(pet.getId());
                    }
                }

                // Persist Pet entity via EntityService (creates a new entity and triggers its workflow)
                CompletableFuture<?> addFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    String.valueOf(Pet.ENTITY_VERSION),
                    pet
                ).thenAccept(uuid -> logger.debug("Persisted pet with returned id: {}", uuid))
                 .exceptionally(ex -> {
                     logger.error("Failed to persist pet: {}", ex.getMessage(), ex);
                     return null;
                 });

                futures.add(addFuture);
            } catch (Exception ex) {
                logger.error("Failed to map/persist item to Pet: {}", ex.getMessage(), ex);
            }
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            // Update fetched count based on successful persist attempts (we consider all provided items attempted)
            job.setFetchedCount(initialCount + items.size());
            job.setStatus("COMPLETED");
            job.setEndTime(Instant.now().toString());
        } catch (Exception ex) {
            logger.error("Error while waiting for pet persist futures: {}", ex.getMessage(), ex);
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setEndTime(Instant.now().toString());
        }

        return job;
    }
}