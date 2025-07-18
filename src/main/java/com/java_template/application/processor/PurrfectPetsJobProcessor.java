package com.java_template.application.processor;

import com.java_template.application.entity.PurrfectPetsJob;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;

@Component
public class PurrfectPetsJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public PurrfectPetsJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PurrfectPetsJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PurrfectPetsJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PurrfectPetsJob.class)
                .validate(PurrfectPetsJob::isValid)
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PurrfectPetsJobProcessor".equals(modelSpec.operationName()) &&
                "purrfectPetsJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PurrfectPetsJob processEntityLogic(PurrfectPetsJob job) {
        try {
            logger.info("Processing PurrfectPetsJob with technicalId: {}", job.getTechnicalId());

            if (job.getOperationType() == null || job.getOperationType().isBlank()) {
                logger.error("Invalid operationType in job {}", job.getTechnicalId());
                job.setStatus("FAILED");
                entityService.updateItem("PurrfectPetsJob", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();
                return job;
            }

            job.setStatus("PROCESSING");
            entityService.updateItem("PurrfectPetsJob", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();

            switch (job.getOperationType()) {
                case "ImportPets":
                    Pet newPet = new Pet();
                    newPet.setName("ImportedPet-" + UUID.randomUUID().toString());
                    newPet.setCategory("cat");
                    newPet.setStatus("AVAILABLE");
                    CompletableFuture<UUID> petIdFuture = entityService.addItem("Pet", Config.ENTITY_VERSION, newPet);
                    UUID petTechnicalId = petIdFuture.get();
                    newPet.setTechnicalId(petTechnicalId);
                    processPet(newPet);
                    logger.info("Imported pet with technicalId: {}", petTechnicalId);
                    break;
                case "SyncFavorites":
                    logger.info("SyncFavorites operation executed for job {}", job.getTechnicalId());
                    break;
                default:
                    logger.error("Unknown operationType {} for job {}", job.getOperationType(), job.getTechnicalId());
                    job.setStatus("FAILED");
                    entityService.updateItem("PurrfectPetsJob", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();
                    return job;
            }

            job.setStatus("COMPLETED");
            entityService.updateItem("PurrfectPetsJob", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();
            logger.info("PurrfectPetsJob {} completed successfully", job.getTechnicalId());
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Processing job {} failed: {}", job.getTechnicalId(), e.getMessage());
            job.setStatus("FAILED");
            try {
                entityService.updateItem("PurrfectPetsJob", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();
            } catch (Exception ex) {
                logger.error("Failed to update job status for job {}: {}", job.getTechnicalId(), ex.getMessage());
            }
        }
        return job;
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());
        logger.info("Pet processed: technicalId={}, name={}, category={}, status={}", pet.getTechnicalId(), pet.getName(), pet.getCategory(), pet.getStatus());
    }
}
