package com.java_template.application.processor;

import com.java_template.application.entity.PetIngestionJob;
import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class PetIngestionJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public PetIngestionJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetIngestionJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(PetIngestionJob.class)
                .validate(this::isValidEntity)
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetIngestionJobProcessor".equals(modelSpec.operationName()) &&
                "petIngestionJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetIngestionJob entity) {
        return entity != null && entity.isValid();
    }

    private PetIngestionJob processEntityLogic(PetIngestionJob job) {
        try {
            logger.info("Processing PetIngestionJob with technicalId: {}", job.getTechnicalId());
            // 1. Update status to PROCESSING
            job.setStatus("PROCESSING");
            entityService.updateItem("PetIngestionJob", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();

            // 2. Simulate fetching pet data from external Petstore API
            // Here, for prototype, we simulate adding a pet
            Pet newPet = new Pet();
            newPet.setName("Simulated Pet");
            newPet.setCategory("cat");
            newPet.setPhotoUrls(Collections.emptyList());
            newPet.setTags(Collections.singletonList("simulated"));
            newPet.setStatus("AVAILABLE");

            UUID petTechnicalId = entityService.addItem("Pet", Config.ENTITY_VERSION, newPet).get();
            newPet.setTechnicalId(petTechnicalId);
            processPet(newPet);

            // 3. Update job status to COMPLETED
            job.setStatus("COMPLETED");
            entityService.updateItem("PetIngestionJob", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();

            logger.info("PetIngestionJob {} completed successfully", job.getTechnicalId());
        } catch (Exception e) {
            try {
                job.setStatus("FAILED");
                entityService.updateItem("PetIngestionJob", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.error("Failed to update job status to FAILED for job {}: {}", job.getTechnicalId(), ex.getMessage());
            }
            logger.error("PetIngestionJob {} failed: {}", job.getTechnicalId(), e.getMessage());
        }
        return job;
    }

    private void processPet(Pet pet) throws ExecutionException, InterruptedException {
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());
        // Enrichment example: add a tag "processed"
        if (!pet.getTags().contains("processed")) {
            pet.getTags().add("processed");
        }
        entityService.updateItem("Pet", Config.ENTITY_VERSION, pet.getTechnicalId(), pet).get();
        logger.info("Pet {} processed and enriched", pet.getTechnicalId());
    }
}
