package com.java_template.application.processor;

import com.java_template.application.entity.PetIngestionJob;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.java_template.common.service.EntityService;

@Component
public class PetIngestionJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PetIngestionJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetIngestionJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetIngestionJob.class)
                .validate(this::isValidEntity)
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetIngestionJobProcessor".equals(modelSpec.operationName()) &&
                "petingestionjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetIngestionJob entity) {
        return entity.isValid();
    }

    private PetIngestionJob processEntityLogic(PetIngestionJob job) {
        try {
            processPetIngestionJob(job);
        } catch (Exception e) {
            logger.error("Error processing PetIngestionJob: {}", e.getMessage(), e);
            job.setStatus("FAILED");
        }
        return job;
    }

    private void processPetIngestionJob(PetIngestionJob job) throws Exception {
        logger.info("Processing PetIngestionJob with technicalId: {}", job.getTechnicalId());

        if (job.getSourceUrl() == null || job.getSourceUrl().isBlank()) {
            job.setStatus("FAILED");
            entityService.updateItem("PetIngestionJob", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();
            logger.error("PetIngestionJob failed validation: sourceUrl is blank");
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }

        job.setStatus("PROCESSING");
        entityService.updateItem("PetIngestionJob", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();

        // Simulate fetching data from Petstore API (simplified for prototype)
        com.java_template.application.entity.Pet newPet = new com.java_template.application.entity.Pet();
        newPet.setName("SamplePetFromIngestion");
        newPet.setCategory("cat");
        newPet.setStatus("AVAILABLE");

        CompletableFuture<UUID> petIdFuture = entityService.addItem("Pet", Config.ENTITY_VERSION, newPet);
        UUID petTechnicalId = petIdFuture.get();
        newPet.setTechnicalId(petTechnicalId);

        processPet(newPet);

        job.setStatus("COMPLETED");
        entityService.updateItem("PetIngestionJob", Config.ENTITY_VERSION, job.getTechnicalId(), job).get();

        logger.info("Completed processing PetIngestionJob with technicalId: {}", job.getTechnicalId());
    }

    private void processPet(com.java_template.application.entity.Pet pet) {
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());

        if (!pet.isValid()) {
            logger.error("Invalid Pet entity with technicalId: {}", pet.getTechnicalId());
            throw new IllegalArgumentException("Pet entity validation failed");
        }

        if ("cat".equalsIgnoreCase(pet.getCategory())) {
            if (pet.getTags() == null) {
                pet.setTags(new ArrayList<>());
            }
            if (!pet.getTags().contains("feline")) {
                pet.getTags().add("feline");
            }
        }

        logger.info("Pet processing complete for technicalId: {}", pet.getTechnicalId());
    }

}