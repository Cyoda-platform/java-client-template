package com.java_template.application.processor;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class PersistPetsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistPetsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistPetsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistPets for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid ingestion job")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob job) {
        return job != null && job.isValid();
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();
        try {
            // Simulate persisting candidates: inspect job.errors for parsed_candidates entry
            long parsed = job.getErrors().stream()
                .filter(s -> s != null && s.startsWith("parsed_candidates="))
                .mapToLong(s -> {
                    try { return Long.parseLong(s.substring(s.indexOf('=') + 1)); } catch (Exception e) { return 0L; }
                }).sum();

            if (parsed == 0) {
                job.getErrors().add("no_candidates_to_persist");
                job.setStatus("failed");
                logger.warn("No candidates found for job {}", job.getId());
                return job;
            }

            // For each candidate, simulate creation
            int created = 0;
            for (int i = 0; i < parsed; i++) {
                Pet pet = new Pet();
                pet.setId(job.getId() + "-persisted-" + (i+1));
                pet.setExternalId(job.getId() + "-ext-persisted-" + (i+1));
                pet.setName("Persisted Pet " + (i+1));
                pet.setSpecies(i % 2 == 0 ? "cat" : "dog");
                pet.setBreed("unknown");
                pet.setAge(1 + i);
                pet.setGender("unknown");
                pet.setStatus("available");
                pet.setCreatedAt(Instant.now().toString());
                pet.setUpdatedAt(Instant.now().toString());
                // Simulate saving by logging
                logger.info("Persisted pet {} for job {}", pet.getId(), job.getId());
                created++;
            }

            job.setImportedCount(job.getImportedCount() + created);
            job.setStatus("completed");
            job.setUpdatedAt(Instant.now().toString());
            logger.info("Job {} persisted {} pets", job.getId(), created);
        } catch (Exception e) {
            job.getErrors().add(e.getMessage());
            job.setStatus("failed");
            logger.error("Error in PersistPetsProcessor for job {}: {}", job.getId(), e.getMessage(), e);
        }
        return job;
    }
}
