package com.java_template.application.processor;

import com.java_template.application.entity.PurrfectPetsJob;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
                .validate(PurrfectPetsJob::isValid, "Invalid PurrfectPetsJob entity state")
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
        logger.info("Processing PurrfectPetsJob with id: {}", job.getId());

        job.setStatus(PurrfectPetsJob.StatusEnum.PROCESSING);

        if (job.getPetType() == null || job.getPetType().isBlank()) {
            logger.error("Invalid petType in job {}", job.getId());
            job.setStatus(PurrfectPetsJob.StatusEnum.FAILED);
            return job;
        }
        if (job.getAction() == null || job.getAction().isBlank()) {
            logger.error("Invalid action in job {}", job.getId());
            job.setStatus(PurrfectPetsJob.StatusEnum.FAILED);
            return job;
        }

        if ("fetch".equalsIgnoreCase(job.getAction())) {
            List<Pet> fetchedPets = new ArrayList<>();

            Pet pet1 = new Pet();
            pet1.setId("pet-" + UUID.randomUUID().toString());
            pet1.setPetId(UUID.randomUUID().toString());
            pet1.setName("SamplePet1");
            pet1.setCategory(job.getPetType());
            pet1.setStatus("available");
            pet1.setPhotoUrls(Collections.emptyList());
            pet1.setLifecycleStatus(Pet.StatusEnum.NEW);
            pet1.setCreatedAt(LocalDateTime.now());

            Pet pet2 = new Pet();
            pet2.setId("pet-" + UUID.randomUUID().toString());
            pet2.setPetId(UUID.randomUUID().toString());
            pet2.setName("SamplePet2");
            pet2.setCategory(job.getPetType());
            pet2.setStatus("available");
            pet2.setPhotoUrls(Collections.emptyList());
            pet2.setLifecycleStatus(Pet.StatusEnum.NEW);
            pet2.setCreatedAt(LocalDateTime.now());

            fetchedPets.add(pet1);
            fetchedPets.add(pet2);

            for (Pet pet : fetchedPets) {
                try {
                    CompletableFuture<UUID> addPetFuture = entityService.addItem("Pet", Config.ENTITY_VERSION, pet);
                    UUID technicalId = addPetFuture.get();
                    logger.info("Added Pet with technicalId: {}", technicalId);
                    processPet(pet);
                } catch (Exception e) {
                    logger.error("Failed to add Pet: {}", e.getMessage(), e);
                    job.setStatus(PurrfectPetsJob.StatusEnum.FAILED);
                    return job;
                }
            }

            job.setStatus(PurrfectPetsJob.StatusEnum.COMPLETED);
            logger.info("Completed processing PurrfectPetsJob with id: {}", job.getId());
        } else {
            logger.error("Unsupported action '{}' in job {}", job.getAction(), job.getId());
            job.setStatus(PurrfectPetsJob.StatusEnum.FAILED);
        }

        return job;
    }

    private Pet processPet(Pet pet) {
        // Business logic for processing Pet entity
        logger.info("Processing Pet with id: {}", pet.getId());

        pet.setLifecycleStatus(Pet.StatusEnum.PROCESSED);

        // Additional processing and validation can be added here

        logger.info("Completed processing Pet with id: {}", pet.getId());
        return pet;
    }

}
