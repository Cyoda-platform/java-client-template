package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
public class CreateInitialSyncJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateInitialSyncJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public CreateInitialSyncJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();

        // Business logic:
        // - When a subscriber becomes active and is verified, create an initial sync Job
        // - The Job created will reference the subscriber and include a payload describing the request
        // - Job creation is done via EntityService.addItem (asynchronous). We do not block the processor.
        // - We avoid updating the triggering entity via EntityService; we may update in-memory fields (e.g., updatedAt)
        try {
            if (!subscriber.isActive()) {
                logger.debug("Subscriber {} is not active; skipping initial sync job creation.", subscriber.getId());
                return subscriber;
            }
            if (!subscriber.isVerified()) {
                logger.debug("Subscriber {} is not verified; skipping initial sync job creation.", subscriber.getId());
                return subscriber;
            }
            // Avoid creating duplicate initial syncs: use subscriber.updatedAt presence as heuristic.
            // If updatedAt equals createdAt, assume this is initial activation and create the job.
            // This is a conservative heuristic given entity lacks explicit initialSync flag.
            boolean shouldCreateInitialSync = true;
            if (subscriber.getCreatedAt() != null && subscriber.getUpdatedAt() != null
                    && subscriber.getCreatedAt().equals(subscriber.getUpdatedAt())) {
                shouldCreateInitialSync = true;
            } else {
                // If updatedAt differs from createdAt, still allow job creation only if preferences are present.
                shouldCreateInitialSync = subscriber.getPreferences() != null;
            }

            if (!shouldCreateInitialSync) {
                logger.debug("Subscriber {} does not meet heuristic for initial sync; skipping.", subscriber.getId());
                return subscriber;
            }

            // Build Job entity ensuring it meets Job.isValid() requirements.
            Job job = new Job();
            String jobId = UUID.randomUUID().toString();
            job.setId(jobId);
            job.setType("initial_sync");
            job.setStatus("pending");
            job.setAttempts(0);
            String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            job.setCreatedAt(now);
            job.setUpdatedAt(now);

            // payload: include subscriber id and preferences (if any)
            Map<String, Object> payload = new HashMap<>();
            payload.put("subscriberId", subscriber.getId());
            if (subscriber.getPreferences() != null) {
                Map<String, Object> prefs = new HashMap<>();
                prefs.put("frequency", subscriber.getPreferences().getFrequency());
                prefs.put("species", subscriber.getPreferences().getSpecies());
                prefs.put("tags", subscriber.getPreferences().getTags());
                payload.put("preferences", prefs);
            }
            job.setPayload(payload);

            // For job validation in this model petIds must be non-empty.
            // Use a wildcard indicator to signal "match all according to preferences".
            job.setPetIds(Collections.singletonList("ALL"));
            // Link the subscriber
            job.setSubscriberIds(Collections.singletonList(subscriber.getId()));
            job.setResult(Collections.emptyMap());

            // Add job asynchronously via EntityService
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Job.ENTITY_NAME,
                String.valueOf(Job.ENTITY_VERSION),
                job
            );

            idFuture.thenAccept(uuid -> logger.info("Created initial sync Job {} for subscriber {} -> persisted id {}", jobId, subscriber.getId(), uuid))
                    .exceptionally(ex -> {
                        logger.error("Failed to create initial sync Job for subscriber {}: {}", subscriber.getId(), ex.getMessage(), ex);
                        return null;
                    });

            // Optionally update in-memory subscriber updatedAt to reflect action (will be persisted by Cyoda)
            subscriber.setUpdatedAt(now);

        } catch (Exception ex) {
            logger.error("Error while creating initial sync job for subscriber {}: {}", subscriber != null ? subscriber.getId() : "unknown", ex.getMessage(), ex);
            // Do not throw — return entity so workflow can continue. Errors logged.
        }

        return subscriber;
    }
}