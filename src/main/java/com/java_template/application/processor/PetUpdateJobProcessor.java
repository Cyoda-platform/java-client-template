package com.java_template.application.processor;

import com.java_template.application.entity.PetUpdateJob;
import com.java_template.application.entity.Pet;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PetUpdateJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public PetUpdateJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetUpdateJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetUpdateJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetUpdateJob.class)
                .validate(PetUpdateJob::isValid)
                .map(this::processPetUpdateJob)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetUpdateJobProcessor".equals(modelSpec.operationName()) &&
               "petUpdateJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetUpdateJob processPetUpdateJob(PetUpdateJob job) {
        logger.info("Processing PetUpdateJob with technicalId: {}", job.getTechnicalId());

        if (job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
            logger.error("PetUpdateJob {} has invalid sourceUrl", job.getTechnicalId());
            job.setStatus("FAILED");
            return job;
        }

        job.setStatus("PROCESSING");

        try {
            Pet fetchedPet = new Pet();
            fetchedPet.setPetId(UUID.randomUUID().toString());
            fetchedPet.setName("MockPet");
            fetchedPet.setCategory("cat");
            fetchedPet.setStatus("AVAILABLE");

            CompletableFuture<UUID> petIdFuture = entityService.addItem(
                    "Pet",
                    Config.ENTITY_VERSION,
                    fetchedPet
            );
            UUID petTechnicalId = petIdFuture.join();
            fetchedPet.setTechnicalId(petTechnicalId);

            processPet(fetchedPet);

            job.setStatus("COMPLETED");
            entityService.addItem("PetUpdateJob", Config.ENTITY_VERSION, job).join();

            logger.info("PetUpdateJob {} completed successfully", job.getTechnicalId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            try {
                entityService.addItem("PetUpdateJob", Config.ENTITY_VERSION, job).join();
            } catch (Exception ex) {
                logger.error("Failed to save failed status for PetUpdateJob {}", job.getTechnicalId(), ex);
            }
            logger.error("Error processing PetUpdateJob {}: {}", job.getTechnicalId(), e.getMessage());
        }
        return job;
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());

        if (pet.getPetId() == null || pet.getPetId().isBlank()
                || pet.getName() == null || pet.getName().isBlank()
                || pet.getCategory() == null || pet.getCategory().isBlank()) {
            logger.error("Pet {} failed validation during processing", pet.getTechnicalId());
            throw new IllegalArgumentException("Pet validation failed: mandatory fields missing");
        }

        logger.info("Pet {} processed successfully with status {}", pet.getTechnicalId(), pet.getStatus());
    }

}
