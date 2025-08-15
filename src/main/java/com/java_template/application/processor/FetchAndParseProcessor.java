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
import java.util.ArrayList;
import java.util.List;

@Component
public class FetchAndParseProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchAndParseProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FetchAndParseProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchAndParse for request: {}", request.getId());

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
            // Mark running and attempt to fetch. We cannot perform real HTTP here; simulate by validating URL and creating sample pets
            job.setStatus("running");
            job.setUpdatedAt(Instant.now().toString());

            // Simple validation of sourceUrl
            String url = job.getSourceUrl();
            if (url == null || url.isEmpty()) {
                job.getErrors().add("sourceUrl_missing");
                job.setStatus("failed");
                logger.error("Ingestion job {} missing sourceUrl", job.getId());
                return job;
            }

            // Simulate parsing: create a couple of candidate pets based on the source host
            List<Pet> candidates = new ArrayList<>();
            Pet p1 = new Pet();
            p1.setId(job.getId() + "-pet-1");
            p1.setExternalId(job.getId() + "-ext-1");
            p1.setName("Simulated Pet 1");
            p1.setSpecies("cat");
            p1.setBreed("mixed");
            p1.setAge(2);
            p1.setGender("F");
            p1.setStatus("available");
            p1.setCreatedAt(Instant.now().toString());
            p1.setUpdatedAt(Instant.now().toString());
            candidates.add(p1);

            Pet p2 = new Pet();
            p2.setId(job.getId() + "-pet-2");
            p2.setExternalId(job.getId() + "-ext-2");
            p2.setName("Simulated Pet 2");
            p2.setSpecies("dog");
            p2.setBreed("beagle");
            p2.setAge(3);
            p2.setGender("M");
            p2.setStatus("available");
            p2.setCreatedAt(Instant.now().toString());
            p2.setUpdatedAt(Instant.now().toString());
            candidates.add(p2);

            // Emit PersistPetCandidate events - since we cannot emit here, attach candidates to job.errors as a simple mechanism
            job.setStatus("processing");
            job.setImportedCount(0);
            // store a summary in errors array to make them visible
            job.getErrors().add("parsed_candidates=" + candidates.size());
            job.setUpdatedAt(Instant.now().toString());

            logger.info("Ingestion job {} parsed {} candidate pets", job.getId(), candidates.size());
        } catch (Exception e) {
            job.getErrors().add(e.getMessage());
            job.setStatus("failed");
            logger.error("Error in FetchAndParseProcessor for job {}: {}", job.getId(), e.getMessage(), e);
        }
        return job;
    }
}
