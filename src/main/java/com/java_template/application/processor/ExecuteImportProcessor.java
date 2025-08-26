package com.java_template.application.processor;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Component
public class ExecuteImportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteImportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ExecuteImportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        return entity != null && entity.isValid();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        // Business logic:
        // 1. Validate source reachability (simple heuristic: non-blank and starts with http/https)
        // 2. Simulate fetch of records (derive a small set of Pet objects based on mode)
        // 3. Add Pet entities via EntityService.addItem()
        // 4. Update job counters (processedCount / failedCount) and status/notes accordingly
        if (job == null) return null;

        logger.info("ExecuteImportProcessor - starting jobId={}, sourceUrl={}, mode={}",
                job.getJobId(), job.getSourceUrl(), job.getMode());

        // defensive defaults: ensure counters are not null
        Integer processed = job.getProcessedCount() != null ? job.getProcessedCount() : 0;
        Integer failed = job.getFailedCount() != null ? job.getFailedCount() : 0;

        // Basic source validation
        String src = job.getSourceUrl();
        if (src == null || src.isBlank() || !(src.startsWith("http://") || src.startsWith("https://"))) {
            job.setStatus("FAILED");
            String prevNotes = job.getNotes() != null ? job.getNotes() + " | " : "";
            job.setNotes(prevNotes + "Source unreachable or invalid: " + src);
            job.setProcessedCount(processed);
            job.setFailedCount(failed);
            logger.warn("ImportJob {} failed validation for sourceUrl={}", job.getJobId(), src);
            return job;
        }

        // Derive a small set of Pet records to import based on mode.
        List<Pet> derivedPets = new ArrayList<>();
        try {
            String mode = job.getMode() != null ? job.getMode().trim().toLowerCase() : "full";
            int count = "incremental".equals(mode) ? 1 : 2; // incremental -> 1 record, full -> 2 records

            for (int i = 1; i <= count; i++) {
                Pet pet = new Pet();
                // Build deterministic ids using jobId to keep traceability
                String petId = (job.getJobId() != null ? job.getJobId() : UUID.randomUUID().toString()) + "-pet-" + i;
                pet.setId(petId);
                pet.setName("Imported Pet " + i);
                pet.setSpecies("cat");
                pet.setBreed("unknown");
                pet.setAge(1 + i - 1);
                pet.setColor("unknown");
                pet.setHealthNotes("");
                pet.setAvatarUrl(null);
                pet.setStatus("PENDING_REVIEW"); // as per workflow initial state
                List<String> tags = new ArrayList<>();
                tags.add("imported");
                pet.setTags(tags);

                Pet.SourceMetadata meta = new Pet.SourceMetadata();
                meta.setPetstoreId(petId);
                Map<String,Object> raw = new HashMap<>();
                raw.put("sourceUrl", job.getSourceUrl());
                raw.put("mode", job.getMode());
                raw.put("requestedBy", job.getRequestedBy());
                meta.setRaw(raw);
                pet.setSourceMetadata(meta);

                derivedPets.add(pet);
            }

            // Persist derived pets via EntityService
            for (Pet p : derivedPets) {
                try {
                    CompletableFuture<UUID> idFuture = entityService.addItem(
                        Pet.ENTITY_NAME,
                        String.valueOf(Pet.ENTITY_VERSION),
                        p
                    );
                    // Wait for completion to ensure counts are accurate
                    idFuture.join();
                    processed++;
                } catch (Exception e) {
                    failed++;
                    logger.error("Failed to add pet {} for job {}: {}", p.getId(), job.getJobId(), e.getMessage(), e);
                }
            }

            // Set final job status based on failures
            job.setProcessedCount(processed);
            job.setFailedCount(failed);
            if (failed > 0) {
                job.setStatus("FAILED");
            } else {
                job.setStatus("COMPLETED");
            }
            String summary = String.format("Imported %d pets, %d failures", processed, failed);
            String prevNotes = job.getNotes() != null ? job.getNotes() + " | " : "";
            job.setNotes(prevNotes + summary);
            logger.info("ImportJob {} completed: {}", job.getJobId(), summary);
        } catch (Exception ex) {
            logger.error("Unexpected error while executing import job {}: {}", job.getJobId(), ex.getMessage(), ex);
            String prevNotes = job.getNotes() != null ? job.getNotes() + " | " : "";
            job.setNotes(prevNotes + "Unexpected error: " + ex.getMessage());
            job.setStatus("FAILED");
            // ensure counters reflect what we processed so far
            job.setProcessedCount(processed);
            job.setFailedCount(failed + 1);
        }

        return job;
    }
}