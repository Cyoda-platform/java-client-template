package com.java_template.application.processor;

import com.java_template.application.entity.PetIngestionJob;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.Arrays;

import com.java_template.application.entity.Pet;
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

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(PetIngestionJob.class)
                .validate(PetIngestionJob::isValid, "Invalid PetIngestionJob state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetIngestionJobProcessor".equals(modelSpec.operationName()) &&
                "petIngestionJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PetIngestionJob processEntityLogic(PetIngestionJob job) {
        try {
            logger.info("Processing PetIngestionJob with ID: {}", job.getId());

            // Update status to PROCESSING by creating new version
            PetIngestionJob processingJob = new PetIngestionJob();
            processingJob.setSource(job.getSource());
            processingJob.setStatus("PROCESSING");
            processingJob.setCreatedAt(job.getCreatedAt());

            CompletableFuture<UUID> updateFuture = entityService.addItem(
                    "PetIngestionJob",
                    Config.ENTITY_VERSION,
                    processingJob
            );
            UUID processingId = updateFuture.get();
            String processingIdStr = processingId.toString();
            processingJob.setId(processingIdStr);
            processingJob.setJobId(processingIdStr);

            logger.info("PetIngestionJob {} status updated to PROCESSING", processingJob.getId());

            // Fetch data from Petstore API (simulated here)
            List<Pet> fetchedPets = new ArrayList<>();

            Pet pet1 = new Pet();
            pet1.setName("Whiskers");
            pet1.setCategory("cat");
            pet1.setPhotoUrls(Arrays.asList("http://image1.jpg", "http://image2.jpg"));
            pet1.setTags(Arrays.asList("cute", "playful"));
            pet1.setStatus("NEW");

            Pet pet2 = new Pet();
            pet2.setName("Barkley");
            pet2.setCategory("dog");
            pet2.setPhotoUrls(Arrays.asList("http://dogimage1.jpg"));
            pet2.setTags(Arrays.asList("friendly", "energetic"));
            pet2.setStatus("NEW");

            fetchedPets.add(pet1);
            fetchedPets.add(pet2);

            // Persist pets and process each
            List<CompletableFuture<UUID>> futures = new ArrayList<>();
            for (Pet pet : fetchedPets) {
                CompletableFuture<UUID> petIdFuture = entityService.addItem(
                        "Pet",
                        Config.ENTITY_VERSION,
                        pet
                );
                futures.add(petIdFuture);
            }

            List<UUID> petIds = new ArrayList<>();
            for (CompletableFuture<UUID> fut : futures) {
                petIds.add(fut.get());
            }

            for (int i = 0; i < fetchedPets.size(); i++) {
                Pet pet = fetchedPets.get(i);
                UUID petId = petIds.get(i);
                String petIdStr = petId.toString();
                pet.setId(petIdStr);
                pet.setPetId(petIdStr);
                logger.info("Persisted Pet from ingestion: {}", pet.getId());
                processPet(pet);
            }

            // Update job status to COMPLETED by creating new version
            PetIngestionJob completedJob = new PetIngestionJob();
            completedJob.setSource(job.getSource());
            completedJob.setStatus("COMPLETED");
            completedJob.setCreatedAt(job.getCreatedAt());

            CompletableFuture<UUID> completedFuture = entityService.addItem(
                    "PetIngestionJob",
                    Config.ENTITY_VERSION,
                    completedJob
            );
            UUID completedId = completedFuture.get();
            String completedIdStr = completedId.toString();
            completedJob.setId(completedIdStr);
            completedJob.setJobId(completedIdStr);

            logger.info("PetIngestionJob {} status updated to COMPLETED", completedJob.getId());

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error processing PetIngestionJob", e);
            // Here you might want to handle failure case and update status to FAILED
        }
        return job;
    }

    private void processPet(Pet pet) {
        // Simulated processing of Pet entity
        logger.info("Processing Pet with ID: {}", pet.getId());
        // Business logic for processing Pet can be implemented here
        // For now, this is a stub to match the prototype
    }
}
