package com.java_template.application.processor;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class NotifyOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotifyOwnerProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Owner.class)
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

    private boolean isValidEntity(Owner entity) {
        return entity != null && entity.isValid();
    }

    private Owner processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Owner> context) {
        Owner entity = context.entity();

        // Business intent:
        // - When an Owner is verified (owner.getVerified() == true) we consider the verification flow completed.
        // - NotifyOwnerProcessor will attempt to gather basic information about the owner's pets (if any)
        //   and create a lightweight IngestionJob-style record to represent that a notification has been dispatched.
        // - We MUST NOT update the triggering Owner via EntityService; modifications to the Owner object returned
        //   will be persisted by the workflow engine automatically. We DO create a separate entity (IngestionJob)
        //   to represent the notification action using EntityService.addItem(...).
        //
        // Implementation details:
        // - If owner is verified -> collect pet names (best-effort) and create a completed IngestionJob record.
        // - If owner is NOT verified -> create a pending IngestionJob record representing a reminder/notification attempt.
        // - All external entity reads/writes are guarded with try/catch to avoid failing the processor unexpectedly.
        // - We do not modify Owner fields here (except possibly ensuring petsOwned is non-null for safety).

        try {
            // Ensure petsOwned is non-null to avoid NPEs
            List<String> petIds = entity.getPetsOwned();
            if (petIds == null) {
                petIds = new ArrayList<>();
                entity.setPetsOwned(petIds); // safe to set; will be persisted by workflow
            }

            // Gather pet names for context (best-effort)
            List<String> petNames = new ArrayList<>();
            for (String petIdStr : petIds) {
                if (petIdStr == null || petIdStr.isBlank()) continue;
                try {
                    UUID petUuid = UUID.fromString(petIdStr);
                    CompletableFuture<DataPayload> payloadFuture = entityService.getItem(petUuid);
                    DataPayload payload = payloadFuture.get();
                    if (payload != null && payload.getData() != null) {
                        Pet pet = objectMapper.treeToValue(payload.getData(), Pet.class);
                        if (pet != null && pet.getName() != null) {
                            petNames.add(pet.getName());
                        }
                    }
                } catch (Exception e) {
                    // Individual pet fetch failures should not prevent notification creation; log and continue
                    logger.warn("Failed to fetch pet {} for owner {}: {}", petIdStr, entity.getId(), e.getMessage());
                }
            }

            // Build a lightweight IngestionJob to represent the notification action
            IngestionJob job = new IngestionJob();
            job.setRequestedBy(entity.getId() != null ? entity.getId() : "unknown");
            job.setSourceUrl("notify://owner/" + (entity.getId() != null ? entity.getId() : "unknown"));
            String now = Instant.now().toString();

            if (Boolean.TRUE.equals(entity.getVerified())) {
                // Notification for verified owner (e.g., welcome / verification confirmed)
                job.setStartedAt(now);
                job.setCompletedAt(now);
                job.setStatus("COMPLETED");
                IngestionJob.Summary summary = new IngestionJob.Summary();
                summary.setCreated(1); // represent one notification record created
                summary.setUpdated(0);
                summary.setFailed(0);
                job.setSummary(summary);

                // Optionally log the notification details
                logger.info("Owner {} verified. Sending welcome notification to {}. Pets: {}", entity.getId(), entity.getEmail(), petNames);
            } else {
                // Reminder notification for unverified owner
                job.setStartedAt(now);
                job.setStatus("PENDING");
                IngestionJob.Summary summary = new IngestionJob.Summary();
                summary.setCreated(0);
                summary.setUpdated(0);
                summary.setFailed(0);
                job.setSummary(summary);

                logger.info("Owner {} not verified. Creating reminder notification record to {}.", entity.getId(), entity.getEmail());
            }

            // Persist the notification job as a separate entity (allowed). Do not update the Owner via EntityService.
            try {
                CompletableFuture<java.util.UUID> addFuture = entityService.addItem(
                    IngestionJob.ENTITY_NAME,
                    IngestionJob.ENTITY_VERSION,
                    job
                );
                java.util.UUID createdId = addFuture.get();
                logger.info("Created notification job {} for owner {}", createdId, entity.getId());
            } catch (Exception e) {
                // Failures in creating the auxiliary entity should be logged but should not crash the processor.
                logger.error("Failed to create notification job for owner {}: {}", entity.getId(), e.getMessage(), e);
            }

        } catch (Exception ex) {
            // Catch-all to ensure processor returns the entity even if internal steps failed.
            logger.error("Unexpected error while processing NotifyOwnerProcessor for owner {}: {}", entity.getId(), ex.getMessage(), ex);
        }

        return entity;
    }
}