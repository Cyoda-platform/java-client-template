package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class JobExecutionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobExecutionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // simple retry policy constant used by processor
    private static final int MAX_ATTEMPTS = 3;

    public JobExecutionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Custom validation to allow ingest jobs that may not have petIds/subscriberIds populated yet.
     * Use a relaxed validation for 'ingest' type jobs (they only need basic fields + payload).
     * For other job types, fall back to the entity's isValid() check.
     */
    private boolean isValidEntity(Job entity) {
        if (entity == null) return false;
        String type = entity.getType() == null ? "" : entity.getType().toLowerCase();

        if ("ingest".equals(type)) {
            return isBasicJobValid(entity);
        }

        // For notify and other types require full entity validity as defined on Job
        return entity.isValid();
    }

    /**
     * Basic job validation: ensures required metadata fields and payload/attempts are present.
     * This is intentionally less strict than Job.isValid() to allow ingest jobs to proceed.
     */
    private boolean isBasicJobValid(Job job) {
        if (job.getId() == null || job.getId().isBlank()) return false;
        if (job.getType() == null || job.getType().isBlank()) return false;
        if (job.getStatus() == null || job.getStatus().isBlank()) return false;
        if (job.getCreatedAt() == null || job.getCreatedAt().isBlank()) return false;
        if (job.getUpdatedAt() == null || job.getUpdatedAt().isBlank()) return false;
        if (job.getAttempts() == null || job.getAttempts() < 0) return false;
        if (job.getPayload() == null) return false;
        return true;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            // Basic safeguard: if attempts exceeded, mark failed
            if (job.getAttempts() != null && job.getAttempts() >= MAX_ATTEMPTS) {
                logger.warn("Job {} exceeded max attempts ({}). Marking as failed.", job.getId(), job.getAttempts());
                job.setStatus("failed");
                job.setLastError("Max attempts exceeded");
                return job;
            }

            String type = job.getType() == null ? "" : job.getType().toLowerCase();
            switch (type) {
                case "ingest":
                    processIngestJob(job);
                    break;
                case "notify":
                    processNotifyJob(job);
                    break;
                default:
                    logger.warn("Unknown job type '{}' for job {}", job.getType(), job.getId());
                    job.setStatus("failed");
                    job.setLastError("Unknown job type: " + job.getType());
            }
        } catch (Exception ex) {
            logger.error("Exception while executing job {}: {}", job.getId(), ex.getMessage(), ex);
            job.setStatus("failed");
            job.setLastError(ex.getMessage());
            // bump attempts
            if (job.getAttempts() == null) job.setAttempts(1);
            else job.setAttempts(job.getAttempts() + 1);
        }
        return job;
    }

    private void processIngestJob(Job job) {
        Map<String, Object> payload = job.getPayload() != null ? job.getPayload() : Collections.emptyMap();
        // Expecting payload may contain an "items" list (from an upstream fetch simulation) - each item is a map
        Object itemsObj = payload.get("items");
        List<String> addedPetIds = new ArrayList<>();
        int fetched = 0;

        if (itemsObj instanceof List) {
            List<?> items = (List<?>) itemsObj;
            fetched = items.size();
            List<CompletableFuture<?>> futures = new ArrayList<>();

            for (Object rawItem : items) {
                try {
                    // convert item to Pet using ObjectMapper to leverage existing fields
                    Pet pet = objectMapper.convertValue(rawItem, Pet.class);
                    // ensure required fields for Pet validity are set
                    if (pet.getId() == null || pet.getId().isBlank()) {
                        pet.setId(UUID.randomUUID().toString());
                    }
                    String ts = job.getCreatedAt() != null ? job.getCreatedAt() : job.getUpdatedAt();
                    if (pet.getCreatedAt() == null || pet.getCreatedAt().isBlank()) pet.setCreatedAt(ts != null ? ts : "");
                    if (pet.getUpdatedAt() == null || pet.getUpdatedAt().isBlank()) pet.setUpdatedAt(ts != null ? ts : "");
                    if (pet.getStatus() == null || pet.getStatus().isBlank()) pet.setStatus("available");

                    // add via entityService (persist other entity types only)
                    CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION),
                        pet
                    );
                    // We won't rely on returned UUID to populate job.petIds; use pet.id we assigned
                    addedPetIds.add(pet.getId());
                    futures.add(idFuture);
                } catch (Exception e) {
                    logger.warn("Failed to convert/add pet item for job {}: {}", job.getId(), e.getMessage());
                }
            }

            // Wait for completions (best-effort)
            for (CompletableFuture<?> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException | ExecutionException e) {
                    logger.warn("Error waiting for addItem future: {}", e.getMessage());
                }
            }
        } else {
            // No items provided in payload; simulate fetch logic: nothing fetched
            fetched = 0;
        }

        // Update job result and state
        Map<String, Object> result = new HashMap<>();
        result.put("fetched", fetched);
        result.put("added", addedPetIds.size());
        job.setResult(result);
        job.setPetIds(addedPetIds);

        job.setStatus("completed");
        // reset lastError on success
        job.setLastError(null);
        // attempts handled externally by workflow; we do not update attempts except on failures
    }

    private void processNotifyJob(Job job) {
        List<String> subscriberIds = job.getSubscriberIds() != null ? job.getSubscriberIds() : Collections.emptyList();
        Map<String, Object> result = new HashMap<>();
        List<String> delivered = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        List<CompletableFuture<?>> futures = new ArrayList<>();

        for (String sid : subscriberIds) {
            if (sid == null || sid.isBlank()) {
                failed.add(sid);
                continue;
            }
            CompletableFuture<ObjectNode> subFuture = entityService.getItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                UUID.fromString(sid)
            );
            futures.add(subFuture.thenAccept(node -> {
                try {
                    // convert node to Subscriber to check flags
                    Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
                    if (subscriber == null) {
                        synchronized (failed) { failed.add(sid); }
                        return;
                    }
                    if (!subscriber.isVerified() || !subscriber.isActive()) {
                        logger.info("Subscriber {} not verified/active; skipping notification", sid);
                        synchronized (failed) { failed.add(sid); }
                        return;
                    }
                    // Build notification payload (simple representation)
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("jobId", job.getId());
                    notification.put("petIds", job.getPetIds());
                    notification.put("payload", job.getPayload());

                    // Simulate delivery attempt: for this implementation we mark as delivered.
                    // (In a real system we'd call the subscriber.contactDetails endpoint/service.)
                    logger.info("Simulated delivery to subscriber {} for job {}", sid, job.getId());
                    synchronized (delivered) { delivered.add(sid); }
                } catch (Exception e) {
                    logger.warn("Error processing subscriber {} for job {}: {}", sid, job.getId(), e.getMessage());
                    synchronized (failed) { failed.add(sid); }
                }
            }));
        }

        // Wait for all lookups/deliveries
        for (CompletableFuture<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.warn("Error waiting for subscriber future: {}", e.getMessage());
            }
        }

        result.put("delivered", delivered);
        result.put("failed", failed);
        result.put("deliveredCount", delivered.size());
        result.put("failedCount", failed.size());

        // Update job state based on delivery results
        if (!failed.isEmpty()) {
            job.setStatus("failed");
            job.setLastError("Some deliveries failed: " + failed.size());
            // increment attempts
            if (job.getAttempts() == null) job.setAttempts(1);
            else job.setAttempts(job.getAttempts() + 1);
        } else {
            job.setStatus("completed");
            job.setLastError(null);
        }

        job.setResult(result);
    }
}