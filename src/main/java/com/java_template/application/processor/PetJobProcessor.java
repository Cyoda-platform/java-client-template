package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetJob;
import com.java_template.common.config.Config;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
                .map(this::processPetJob)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobProcessor".equals(modelSpec.operationName()) &&
                "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetJob processPetJob(PetJob petJob) {
        try {
            logger.info("Processing PetJob with ID: {}", petJob.getId());
            if (petJob.getSourceUrl() == null || petJob.getSourceUrl().isBlank()) {
                petJob.setStatus("FAILED");
                logger.error("PetJob sourceUrl is invalid");
                updatePetJobStatus(petJob);
                return petJob;
            }

            petJob.setStatus("PROCESSING");
            updatePetJobStatus(petJob);

            Pet pet1 = new Pet();
            pet1.setPetId(UUID.randomUUID().toString());
            pet1.setName("Fluffy");
            pet1.setSpecies("Cat");
            pet1.setBreed("Persian");
            pet1.setAge(3);
            pet1.setStatus("NEW");
            CompletableFuture<UUID> pet1IdFuture = entityService.addItem("Pet", Config.ENTITY_VERSION, pet1);
            pet1.setTechnicalId(pet1IdFuture.get());
            processPet(pet1);

            Pet pet2 = new Pet();
            pet2.setPetId(UUID.randomUUID().toString());
            pet2.setName("Buddy");
            pet2.setSpecies("Dog");
            pet2.setBreed("Golden Retriever");
            pet2.setAge(5);
            pet2.setStatus("NEW");
            CompletableFuture<UUID> pet2IdFuture = entityService.addItem("Pet", Config.ENTITY_VERSION, pet2);
            pet2.setTechnicalId(pet2IdFuture.get());
            processPet(pet2);

            petJob.setStatus("COMPLETED");
            updatePetJobStatus(petJob);
            logger.info("PetJob processing completed successfully for ID: {}", petJob.getId());
        } catch (Exception e) {
            logger.error("Error processing PetJob", e);
            petJob.setStatus("FAILED");
            try {
                updatePetJobStatus(petJob);
            } catch (Exception ex) {
                logger.error("Error updating PetJob status after failure", ex);
            }
        }
        return petJob;
    }

    private void updatePetJobStatus(PetJob petJob) throws Exception {
        entityService.updateItem("PetJob", Config.ENTITY_VERSION, petJob.getTechnicalId(), petJob).get();
    }

    private Pet processPet(Pet pet) {
        // Basic processing logic could be here if needed
        return pet;
    }
}
