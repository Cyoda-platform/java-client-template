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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
                .validate(PurrfectPetsJob::isValid, "Invalid entity state")
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
        logger.info("Processing PurrfectPetsJob with technicalId: {}", job.getTechnicalId());

        // Validation of actionType
        String action = job.getActionType().toUpperCase(Locale.ROOT).trim();
        if (!action.equals("FETCH_PETS") && !action.equals("UPDATE_PET_STATUS")) {
            logger.error("Invalid actionType: {}", job.getActionType());
            job.setStatus("FAILED");
            // Update job status in external service by creating a new version
            entityService.addItem("purrfectPetsJob", Config.ENTITY_VERSION, job)
                    .thenAccept(ignored -> {});
            return job;
        }

        job.setStatus("PROCESSING");

        if (action.equals("FETCH_PETS")) {
            // Simulate fetching pets from Petstore API and creating Pet entities
            // Here we simulate with dummy data for prototype

            Pet samplePet1 = new Pet();
            samplePet1.setName("Simba");
            samplePet1.setType("cat");
            samplePet1.setBreed("Siamese");
            samplePet1.setAge(3);
            samplePet1.setAvailabilityStatus("AVAILABLE");
            samplePet1.setStatus("NEW");

            Pet samplePet2 = new Pet();
            samplePet2.setName("Buddy");
            samplePet2.setType("dog");
            samplePet2.setBreed("Beagle");
            samplePet2.setAge(5);
            samplePet2.setAvailabilityStatus("AVAILABLE");
            samplePet2.setStatus("NEW");

            List<Pet> fetchedPets = List.of(samplePet1, samplePet2);

            // Validate pets and add them via entity service
            List<Pet> validPets = new ArrayList<>();
            for (Pet pet : fetchedPets) {
                if (pet.isValid()) {
                    validPets.add(pet);
                } else {
                    logger.error("Invalid pet data during fetch: {}", pet);
                }
            }

            entityService.addItems("pet", Config.ENTITY_VERSION, validPets)
                    .thenCompose(petTechnicalIds -> {
                        CompletableFuture<Void> allProcesses = CompletableFuture.allOf(
                                petTechnicalIds.stream().map(id -> {
                                    // Retrieve pet by id to get full object and process - but we have pet objects locally
                                    // Since we have the pets locally, assign technicalId and process them
                                    int idx = petTechnicalIds.indexOf(id);
                                    Pet pet = validPets.get(idx);
                                    pet.setTechnicalId(id);
                                    return processPet(pet);
                                }).toArray(CompletableFuture[]::new)
                        );
                        return allProcesses.thenCompose(v -> {
                            job.setStatus("COMPLETED");
                            return entityService.addItem("purrfectPetsJob", Config.ENTITY_VERSION, job).thenAccept(ignored -> {});
                        });
                    })
                    .exceptionally(e -> {
                        logger.error("Error processing pets fetch", e);
                        job.setStatus("FAILED");
                        entityService.addItem("purrfectPetsJob", Config.ENTITY_VERSION, job);
                        return null;
                    });
        } else if (action.equals("UPDATE_PET_STATUS")) {
            // For prototype, no specific update logic implemented
            logger.info("Update pet status action requested, no implementation in prototype");
            job.setStatus("COMPLETED");
            entityService.addItem("purrfectPetsJob", Config.ENTITY_VERSION, job).thenAccept(ignored -> {});
        }

        return job;
    }

    private CompletableFuture<Void> processPet(Pet pet) {
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());

        if (!pet.isValid()) {
            logger.error("Validation failed for Pet technicalId: {}", pet.getTechnicalId());
            return CompletableFuture.completedFuture(null);
        }

        if ("NEW".equalsIgnoreCase(pet.getStatus())) {
            pet.setStatus("ACTIVE");
            // Create new version with updated status
            return entityService.addItem("pet", Config.ENTITY_VERSION, pet)
                    .thenAccept(id -> logger.info("Pet status updated to ACTIVE for technicalId: {}", id));
        }

        // Further processing could involve notifications, indexing, etc.
        return CompletableFuture.completedFuture(null);
    }

}
