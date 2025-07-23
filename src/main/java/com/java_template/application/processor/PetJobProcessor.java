package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetJob;
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

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class PetJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public PetJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetJob.class)
                .validate(this::isValidEntity, "Invalid PetJob state: sourceUrl required")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
                "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetJob entity) {
        return entity != null && entity.getSourceUrl() != null && !entity.getSourceUrl().isBlank();
    }

    private PetJob processEntityLogic(PetJob petJob) {
        logger.info("Processing PetJob with ID: {}", petJob.getJobId());
        try {
            if (petJob.getSourceUrl() == null || petJob.getSourceUrl().isBlank()) {
                petJob.setStatus("FAILED");
                updatePetJobStatus(petJob);
                logger.error("PetJob sourceUrl is invalid");
                return petJob;
            }
            petJob.setStatus("PROCESSING");
            updatePetJobStatus(petJob);

            // Simulate fetching pet data from external Petstore API
            // Create dummy pets as in prototype
            Pet pet1 = new Pet();
            pet1.setPetId(UUID.randomUUID().toString());
            pet1.setName("Fluffy");
            pet1.setSpecies("Cat");
            pet1.setBreed("Persian");
            pet1.setAge(3);
            pet1.setStatus("NEW");
            pet1.setTechnicalId(UUID.randomUUID());

            CompletableFuture<UUID> pet1IdFuture = entityService.addItem("Pet", Integer.parseInt(Config.ENTITY_VERSION), pet1);
            UUID pet1TechId = pet1IdFuture.get();
            pet1.setTechnicalId(pet1TechId);

            Pet pet2 = new Pet();
            pet2.setPetId(UUID.randomUUID().toString());
            pet2.setName("Buddy");
            pet2.setSpecies("Dog");
            pet2.setBreed("Golden Retriever");
            pet2.setAge(5);
            pet2.setStatus("NEW");
            pet2.setTechnicalId(UUID.randomUUID());

            CompletableFuture<UUID> pet2IdFuture = entityService.addItem("Pet", Integer.parseInt(Config.ENTITY_VERSION), pet2);
            UUID pet2TechId = pet2IdFuture.get();
            pet2.setTechnicalId(pet2TechId);

            // Potentially further processing or notifications for pets could be triggered here

            petJob.setStatus("COMPLETED");
            updatePetJobStatus(petJob);
            logger.info("PetJob processing completed successfully for ID: {}", petJob.getJobId());
        } catch (InterruptedException | ExecutionException e) {
            petJob.setStatus("FAILED");
            try {
                updatePetJobStatus(petJob);
            } catch (Exception ex) {
                logger.error("Failed to update PetJob status after processing failure", ex);
            }
            logger.error("Error processing PetJob with ID: {}", petJob.getJobId(), e);
        } catch (Exception e) {
            logger.error("Unexpected error processing PetJob with ID: {}", petJob.getJobId(), e);
        }
        return petJob;
    }

    private void updatePetJobStatus(PetJob petJob) throws Exception {
        entityService.updateItem("PetJob", Integer.parseInt(Config.ENTITY_VERSION), petJob.getTechnicalId(), petJob).get();
    }
}
