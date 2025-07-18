package com.java_template.application.processor;

import com.java_template.application.entity.PetUpdateJob;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class PetUpdateJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PetUpdateJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .validate(PetUpdateJob::isValid, "Invalid PetUpdateJob state")
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
        logger.info("Processing PetUpdateJob with id: {}", job.getId());

        try {
            job.setStatus("PROCESSING");

            try {
                entityService.addItem("PetUpdateJob", Config.ENTITY_VERSION, job).get();
            } catch (Exception e) {
                logger.error("Failed to update PetUpdateJob status to PROCESSING: {}", e.getMessage());
            }

            Pet dummyPet = new Pet();
            dummyPet.setName("Simulated Pet");
            dummyPet.setCategory("Cat");
            dummyPet.setStatus("AVAILABLE");
            dummyPet.setTags(Arrays.asList("simulated", "cat"));
            dummyPet.setPhotoUrls(Arrays.asList("http://example.com/simulated.jpg"));

            CompletableFuture<java.util.UUID> petIdFuture = entityService.addItem("Pet", Config.ENTITY_VERSION, dummyPet);
            java.util.UUID petTechnicalId = petIdFuture.get();
            String petId = petTechnicalId.toString();
            dummyPet.setId(petId);
            dummyPet.setPetId(petId);

            processPet(dummyPet);

            job.setStatus("COMPLETED");
            try {
                entityService.addItem("PetUpdateJob", Config.ENTITY_VERSION, job).get();
            } catch (Exception e) {
                logger.error("Failed to update PetUpdateJob status to COMPLETED: {}", e.getMessage());
            }

            logger.info("PetUpdateJob {} completed successfully", job.getId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            try {
                entityService.addItem("PetUpdateJob", Config.ENTITY_VERSION, job).get();
            } catch (Exception ex) {
                logger.error("Failed to update PetUpdateJob status to FAILED: {}", ex.getMessage());
            }
            logger.error("Error processing PetUpdateJob {}: {}", job.getId(), e.getMessage());
        }
        return job;
    }

    private Pet processPet(Pet pet) {
        logger.info("Processing Pet with id: {}", pet.getId());

        if (pet.getName() == null || pet.getName().isBlank()) {
            logger.error("Pet name is invalid");
            return pet;
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            logger.error("Pet status is invalid");
            return pet;
        }

        if (pet.getTags() != null) {
            List<String> normalizedTags = new ArrayList<>();
            for (String tag : pet.getTags()) {
                normalizedTags.add(tag.toLowerCase());
            }
            pet.setTags(normalizedTags);
        }

        logger.info("Pet {} processed successfully", pet.getId());
        return pet;
    }
}
