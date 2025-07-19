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
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
                .map(entity -> {
                    try {
                        return processPurrfectPetsJob(entity);
                    } catch (ExecutionException | InterruptedException e) {
                        logger.error("Error processing PurrfectPetsJob", e);
                        entity.setStatus("FAILED");
                        return entity;
                    }
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PurrfectPetsJobProcessor".equals(modelSpec.operationName()) &&
               "purrfectPetsJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PurrfectPetsJob processPurrfectPetsJob(PurrfectPetsJob job) throws ExecutionException, InterruptedException {
        logger.info("Processing PurrfectPetsJob with ID: {}", job.getId());
        if (job.getAction() == null || job.getAction().isBlank()) {
            logger.error("Job action is missing");
            job.setStatus("FAILED");
            return job;
        }
        if ("ingestPetData".equals(job.getAction())) {
            logger.info("Ingesting pet data from payload: {}", job.getPayload());

            // Simulate creating one pet entity from ingestion
            Pet newPet = new Pet();
            newPet.setName("Sample Pet");
            newPet.setCategory("cat");
            newPet.setStatus("NEW");
            newPet.setPhotoUrls(new ArrayList<>());
            newPet.setTags(new ArrayList<>());

            CompletableFuture<UUID> petIdFuture = entityService.addItem("Pet", Config.ENTITY_VERSION, newPet);
            UUID petTechnicalId = petIdFuture.get();
            String petTechIdStr = petTechnicalId.toString();
            newPet.setId(petTechIdStr);
            newPet.setPetId(petTechIdStr);

            processPet(newPet);

            logger.info("Created new pet '{}' from ingestion", newPet.getName());
            job.setStatus("COMPLETED");
        } else {
            logger.error("Unknown job action: {}", job.getAction());
            job.setStatus("FAILED");
        }
        return job;
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet with ID: {}", pet.getId());
        if ("NEW".equalsIgnoreCase(pet.getStatus())) {
            pet.setStatus("ACTIVE");
            logger.info("Pet {} status set to ACTIVE", pet.getId());
        }
        logger.info("Pet {} is ready for retrieval", pet.getId());
    }

}
