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
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class PetIngestionJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public PetIngestionJobProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetIngestionJob.class)
            .validate(this::isValidEntity, "Invalid PetIngestionJob state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PetIngestionJob entity) {
        return entity != null && entity.isValid();
    }

    private PetIngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetIngestionJob> context) {
        PetIngestionJob job = context.entity();
        String technicalId = context.request().getEntityId();

        job.setStatus("IN_PROGRESS");
        job.setStartTime(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        logger.info("PetIngestionJob {} status updated to IN_PROGRESS. StartTime: {}", technicalId, job.getStartTime());

        int successfulIngestedCount = 0;
        String jobErrorMessage = null;
        try {
            List<Pet> fetchedPets = simulatePetstoreApiCall(job.getTargetPetStatus());
            logger.info("Fetched {} pets from simulated Petstore API for job {}", fetchedPets.size(), technicalId);

            for (Pet rawPetData : fetchedPets) {
                try {
                    String funFact = generateFunFact(rawPetData);
                    rawPetData.setFunFact(funFact);

                    CompletableFuture<UUID> addPetFuture = entityService.addItem(
                        Pet.ENTITY_NAME,
                        String.valueOf(Config.ENTITY_VERSION),
                        rawPetData
                    );
                    addPetFuture.get();
                    successfulIngestedCount++;
                    logger.debug("Successfully processed and saved Pet with ID: {}", rawPetData.getPetstoreId());

                } catch (Exception e) {
                    logger.error("Error processing single pet for job {}: {}", technicalId, e.getMessage());
                    if (jobErrorMessage == null) {
                        jobErrorMessage = "Partial ingestion failure: " + e.getMessage();
                    } else {
                        jobErrorMessage += "; " + e.getMessage();
                    }
                }
            }

            job.setIngestedPetCount(successfulIngestedCount);
            if (jobErrorMessage == null) {
                job.setStatus("COMPLETED");
                logger.info("PetIngestionJob {} completed successfully. Ingested {} pets.", technicalId, successfulIngestedCount);
            } else {
                job.setStatus("FAILED");
                job.setErrorMessage(jobErrorMessage);
                logger.warn("PetIngestionJob {} completed with partial failures. Ingested {} pets. Error: {}", technicalId, successfulIngestedCount, jobErrorMessage);
            }


        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage("Job failed due to: " + e.getMessage());
            job.setIngestedPetCount(successfulIngestedCount);
            logger.error("PetIngestionJob {} failed: {}", technicalId, e.getMessage(), e);
        } finally {
            job.setEndTime(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            logger.info("PetIngestionJob {} EndTime: {}", technicalId, job.getEndTime());
        }

        return job;
    }

    private List<Pet> simulatePetstoreApiCall(String targetStatus) {
        logger.info("Simulating Petstore API call for status: {}", targetStatus);

        List<Pet> pets = new ArrayList<>();

        Pet pet1 = new Pet();
        pet1.setPetstoreId(12345L);
        pet1.setName("Buddy");
        pet1.setStatus(targetStatus);
        pet1.setCategory("Dog");
        pet1.setPhotoUrls(List.of("http://example.com/buddy.jpg"));
        pet1.setTags(List.of("friendly", "playful"));

        Pet pet2 = new Pet();
        pet2.setPetstoreId(67890L);
        pet2.setName("Whiskers");
        pet2.setStatus(targetStatus);
        pet2.setCategory("Cat");
        pet2.setPhotoUrls(List.of("http://example.com/whiskers.jpg"));
        pet2.setTags(List.of("cute", "sleepy"));

        pets.add(pet1);
        pets.add(pet2);

        return pets;
    }

    private String generateFunFact(Pet pet) {
        switch (pet.getCategory() != null ? pet.getCategory().toLowerCase() : "") {
            case "dog":
                return pet.getName() + " loves chasing squirrels in the park!";
            case "cat":
                return pet.getName() + " can sleep up to 16 hours a day!";
            default:
                return pet.getName() + " has a unique hidden talent!";
        }
    }
}
