package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.petsyncjob.version_1.PetSyncJob;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * PersistProcessor for PetSyncJob: takes a PetSyncJob entity (with config containing items)
 * and persists mapped Pet entities using EntityService. Updates fetchedCount and job status.
 */
@Component
public class PersistProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistProcessor(SerializerFactory serializerFactory,
                            EntityService entityService,
                            ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetSyncJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetSyncJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PetSyncJob job) {
        return job != null && job.isValid();
    }

    private PetSyncJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetSyncJob> context) {
        PetSyncJob job = context.entity();
        if (job == null) return null;

        try {
            Map<String, Object> config = job.getConfig();
            if (config == null) {
                logger.warn("PetSyncJob {} has no config; nothing to persist", job.getId());
                job.setStatus("FAILED");
                job.setErrorMessage("Missing config");
                return job;
            }

            Object itemsObj = config.get("items");
            // Support alternate key names commonly used
            if (itemsObj == null) {
                itemsObj = config.get("pets");
            }

            int addedCount = 0;
            if (itemsObj instanceof List<?> itemsList) {
                for (Object item : itemsList) {
                    try {
                        // Convert item (Map or JsonNode) to Pet using ObjectMapper
                        Pet pet = objectMapper.convertValue(item, Pet.class);

                        // Ensure required fields and defaults according to entity validation
                        if (pet.getId() == null || pet.getId().isBlank()) {
                            pet.setId("pet_" + UUID.randomUUID().toString());
                        }
                        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
                            pet.setStatus("available");
                        }
                        if (pet.getSpecies() == null || pet.getSpecies().isBlank()) {
                            // If species not provided, try to infer from config mapping or set unknown
                            pet.setSpecies("unknown");
                        }

                        // Persist pet using entityService (do not update the triggering entity via entityService)
                        CompletableFuture<UUID> idFuture = entityService.addItem(
                            Pet.ENTITY_NAME,
                            String.valueOf(Pet.ENTITY_VERSION),
                            pet
                        );
                        // Fire-and-forget: do not block processing for long-running persistence
                        idFuture.whenComplete((uuid, ex) -> {
                            if (ex != null) {
                                logger.error("Failed to add Pet from job {}: {}", job.getId(), ex.getMessage());
                            } else {
                                logger.debug("Added Pet with technical id {} for job {}", uuid, job.getId());
                            }
                        });

                        addedCount++;
                    } catch (Exception e) {
                        // Log and continue with next item
                        logger.warn("Failed to process item in PetSyncJob {}: {}", job.getId(), e.getMessage(), e);
                    }
                }
            } else {
                logger.warn("No items list found in PetSyncJob {} config (found type {}).", job.getId(),
                    itemsObj == null ? "null" : itemsObj.getClass().getSimpleName());
            }

            // Update fetchedCount on the job entity (Cyoda will persist this entity automatically)
            Integer prev = job.getFetchedCount();
            job.setFetchedCount((prev == null ? 0 : prev) + addedCount);

            // Mark job as completed if we processed at least one item; otherwise keep status as persisting or failed
            if (addedCount > 0) {
                job.setStatus("COMPLETED");
            } else {
                // If no items were added, set to FAILED with a message
                job.setStatus("FAILED");
                job.setErrorMessage(job.getErrorMessage() == null ? "No items persisted" : job.getErrorMessage());
            }

            logger.info("PetSyncJob {} processed: added={}, fetchedCount={}", job.getId(), addedCount, job.getFetchedCount());
        } catch (Exception ex) {
            logger.error("Error processing PetSyncJob {}: {}", job.getId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
        }

        return job;
    }
}